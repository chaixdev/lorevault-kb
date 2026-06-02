package com.lorevault.api.library.universe;

import static com.lorevault.api.common.StringSanitizer.toSnakeCase;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Top-level content universe (e.g., "Cosmere").
 * Provides a stable UUID and a normalized slug for consistent referencing.
 */
@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Node("Universe")
public class Universe {
	@Id
	private UUID id;
	private String name;
	private String slug; // normalized, URL/path-safe identifier
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	/**
	 * Convenience factory to construct a Universe from a display name.
	 */
	public static Universe ofName(String name) {
		Universe u = new Universe();
		u.setId(UUID.randomUUID());
		u.setName(name);
		u.setSlug(toSnakeCase(name));
		u.setCreatedAt(LocalDateTime.now());
		u.setUpdatedAt(u.getCreatedAt());
		return u;
	}
}
