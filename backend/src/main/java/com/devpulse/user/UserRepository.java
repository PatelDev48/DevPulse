package com.devpulse.user;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

	private static final RowMapper<User> USER_ROW_MAPPER = (resultSet, rowNumber) -> new User(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("name"),
			resultSet.getString("email"),
			resultSet.getString("password_hash"),
			resultSet.getObject("created_at", OffsetDateTime.class).toInstant());

	private final JdbcTemplate jdbcTemplate;

	public UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public User create(String name, String normalizedEmail, String passwordHash) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO devpulse.users (id, name, email, password_hash)
				VALUES (?, ?, ?, ?)
				RETURNING id, name, email, password_hash, created_at
				""", USER_ROW_MAPPER, UUID.randomUUID(), name, normalizedEmail, passwordHash);
	}

	public Optional<User> findBySupabaseUserId(UUID supabaseUserId) {
    return DataAccessUtils.optionalResult(jdbcTemplate.query("""
            SELECT id, name, email, password_hash, created_at
            FROM devpulse.users
            WHERE supabase_user_id = ?
            """, USER_ROW_MAPPER, supabaseUserId));
	}

	public Optional<User> linkSupabaseUserId(UUID userId, UUID supabaseUserId) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				UPDATE devpulse.users
				SET supabase_user_id = ?
				WHERE id = ? AND supabase_user_id IS NULL
				RETURNING id, name, email, password_hash, created_at
				""", USER_ROW_MAPPER, supabaseUserId, userId));
	}

	public User createSupabaseUser(UUID supabaseUserId, String name, String normalizedEmail) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO devpulse.users (id, name, email, password_hash, supabase_user_id)
				VALUES (?, ?, ?, NULL, ?)
				RETURNING id, name, email, password_hash, created_at
				""", USER_ROW_MAPPER, supabaseUserId, name, normalizedEmail, supabaseUserId);
	}

	public Optional<User> findByEmail(String normalizedEmail) {
		return DataAccessUtils.optionalResult(jdbcTemplate.query("""
				SELECT id, name, email, password_hash, created_at
				FROM devpulse.users
				WHERE email = ?
				""", USER_ROW_MAPPER, normalizedEmail));
	}
}