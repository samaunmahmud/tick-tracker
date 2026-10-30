package com.samaun.ticktracker.insights;

import com.samaun.ticktracker.common.ApiError.FieldError;
import com.samaun.ticktracker.common.InvalidRequestException;
import com.samaun.ticktracker.sighting.SightingFilter;
import com.samaun.ticktracker.sighting.SightingFilterParams;
import com.samaun.ticktracker.sighting.SightingSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InsightsService {

    private static final int MAX_TREND_POINTS = 2000;

    private final NamedParameterJdbcTemplate jdbc;

    public InsightsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InsightsSummary summary(SightingFilterParams params) {
        SqlFilter filter = toSql(parse(params, new ArrayList<>()));

        String totalsSql = """
                SELECT count(*) AS total, min(sighted_at) AS first_sighting, max(sighted_at) AS last_sighting
                FROM sightings
                %s
                """.formatted(filter.whereClause());
        String bySourceSql = """
                SELECT source, count(*) AS sightings
                FROM sightings
                %s
                GROUP BY source
                """.formatted(filter.whereClause());
        String lastSyncSql = "SELECT max(finished_at) AS last_sync FROM sync_runs WHERE status = 'SUCCESS'";

        Map<SightingSource, Long> bySource = new EnumMap<>(SightingSource.class);
        for (SightingSource source : SightingSource.values()) {
            bySource.put(source, 0L);
        }
        jdbc.query(bySourceSql, filter.parameters(), row -> {
            bySource.put(SightingSource.valueOf(row.getString("source")), row.getLong("sightings"));
        });

        OffsetDateTime lastSync = jdbc.queryForObject(lastSyncSql, new MapSqlParameterSource(),
                (row, rowNumber) -> row.getObject("last_sync", OffsetDateTime.class));

        return jdbc.queryForObject(totalsSql, filter.parameters(), (row, rowNumber) -> new InsightsSummary(
                row.getLong("total"),
                row.getObject("first_sighting", LocalDateTime.class),
                row.getObject("last_sighting", LocalDateTime.class),
                bySource,
                lastSync == null ? null : lastSync.toInstant()));
    }

    public List<CountByKey> countByCity(SightingFilterParams params) {
        return countBy("city", params);
    }

    public List<CountByKey> countByRegion(SightingFilterParams params) {
        return countBy("region", params);
    }

    public List<CountByKey> countBySpecies(SightingFilterParams params) {
        return countBy("species", params);
    }

    public List<TrendPoint> trends(SightingFilterParams params, String intervalName) {
        List<FieldError> errors = new ArrayList<>();
        SightingFilter filter = SightingFilter.parse(params, errors);
        TrendInterval interval = TrendInterval.fromName(intervalName).orElse(null);
        if (interval == null) {
            errors.add(new FieldError("interval", "must be 'week', 'month' or 'year'"));
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }

        SqlFilter sqlFilter = toSql(filter);
        MapSqlParameterSource parameters = sqlFilter.parameters()
                .addValue("truncateTo", interval.truncateTo())
                .addValue("step", interval.step())
                .addValue("rangeStart", filter.from())
                .addValue("rangeEnd", filter.to())
                .addValue("pointLimit", MAX_TREND_POINTS + 1);
        String sql = """
                WITH counts AS (
                    SELECT date_trunc(:truncateTo, sighted_at) AS period, count(*) AS sightings
                    FROM sightings
                    %s
                    GROUP BY 1
                ),
                bounds AS (
                    SELECT COALESCE(date_trunc(:truncateTo, CAST(:rangeStart AS TIMESTAMP)), min(period)) AS first_period,
                           COALESCE(date_trunc(:truncateTo, CAST(:rangeEnd AS TIMESTAMP)), max(period)) AS last_period
                    FROM counts
                )
                SELECT series.period, COALESCE(counts.sightings, 0) AS sightings
                FROM bounds
                CROSS JOIN generate_series(bounds.first_period, bounds.last_period, CAST(:step AS INTERVAL)) AS series(period)
                LEFT JOIN counts ON counts.period = series.period
                ORDER BY series.period
                LIMIT :pointLimit
                """.formatted(sqlFilter.whereClause());

        List<TrendPoint> points = jdbc.query(sql, parameters, (row, rowNumber) -> new TrendPoint(
                row.getObject("period", LocalDateTime.class).toLocalDate(),
                row.getLong("sightings")));
        if (points.size() > MAX_TREND_POINTS) {
            throw new InvalidRequestException(List.of(new FieldError("interval", "this date range has more than "
                    + MAX_TREND_POINTS + " " + interval.truncateTo() + "s; use a longer interval or a shorter range")));
        }
        return points;
    }

    public List<SeasonalPoint> seasonal(SightingFilterParams params) {
        SqlFilter filter = toSql(parse(params, new ArrayList<>()));
        String sql = """
                SELECT months.month, COALESCE(counts.sightings, 0) AS sightings
                FROM generate_series(1, 12) AS months(month)
                LEFT JOIN (
                    SELECT CAST(EXTRACT(MONTH FROM sighted_at) AS INTEGER) AS month, count(*) AS sightings
                    FROM sightings
                    %s
                    GROUP BY 1
                ) AS counts ON counts.month = months.month
                ORDER BY months.month
                """.formatted(filter.whereClause());

        return jdbc.query(sql, filter.parameters(), (row, rowNumber) -> {
            int month = row.getInt("month");
            return new SeasonalPoint(month, Month.of(month).getDisplayName(TextStyle.FULL, Locale.UK),
                    row.getLong("sightings"));
        });
    }

    private List<CountByKey> countBy(String column, SightingFilterParams params) {
        SqlFilter filter = toSql(parse(params, new ArrayList<>()));
        String sql = """
                SELECT %1$s AS key, count(*) AS sightings
                FROM sightings
                %2$s
                GROUP BY %1$s
                ORDER BY sightings DESC, key
                """.formatted(column, filter.whereClause());

        return jdbc.query(sql, filter.parameters(),
                (row, rowNumber) -> new CountByKey(row.getString("key"), row.getLong("sightings")));
    }

    private SightingFilter parse(SightingFilterParams params, List<FieldError> errors) {
        SightingFilter filter = SightingFilter.parse(params, errors);
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return filter;
    }

    private SqlFilter toSql(SightingFilter filter) {
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.from() != null) {
            conditions.add("sighted_at >= :from");
            parameters.addValue("from", filter.from());
        }
        if (filter.to() != null) {
            conditions.add("sighted_at <= :to");
            parameters.addValue("to", filter.to());
        }
        if (filter.city() != null) {
            conditions.add("city = :city");
            parameters.addValue("city", filter.city());
        }
        if (filter.region() != null) {
            conditions.add("region = :region");
            parameters.addValue("region", filter.region());
        }
        if (filter.species() != null) {
            conditions.add("species = :species");
            parameters.addValue("species", filter.species());
        }
        String whereClause = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new SqlFilter(whereClause, parameters);
    }

    private record SqlFilter(String whereClause, MapSqlParameterSource parameters) {
    }
}
