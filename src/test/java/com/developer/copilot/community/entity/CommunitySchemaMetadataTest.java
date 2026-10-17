package com.developer.copilot.community.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.mapping.CheckConstraint;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import com.developer.copilot.auth.entity.User;
import com.developer.copilot.community.repository.CommunityAccountRepository;
import com.developer.copilot.community.repository.CommunityMembershipRepository;
import com.developer.copilot.community.repository.CommunityMessageRepository;
import com.developer.copilot.community.repository.CommunityRepository;
import com.developer.copilot.community.repository.CommunityUrlAliasRepository;

import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

class CommunitySchemaMetadataTest {

    private static StandardServiceRegistry registry;
    private static Metadata metadata;

    @BeforeAll
    static void mapCommunitySchema() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, MySQLDialect.class.getName())
                .applySetting(AvailableSettings.ALLOW_METADATA_ON_BOOT, "false")
                .applySetting(AvailableSettings.JAKARTA_HBM2DDL_DB_MAJOR_VERSION, "8")
                .applySetting(AvailableSettings.JAKARTA_HBM2DDL_DB_MINOR_VERSION, "0")
                .build();
        metadata = new MetadataSources(registry)
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(CommunityAccount.class)
                .addAnnotatedClass(Community.class)
                .addAnnotatedClass(CommunityUrlAlias.class)
                .addAnnotatedClass(CommunityMembership.class)
                .addAnnotatedClass(CommunityMessage.class)
                .buildMetadata();
    }

    @AfterAll
    static void closeRegistry() {
        if (registry != null) {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void accountIsOnePerUserAndStoresConsentOnly() {
        Table table = table(CommunityAccount.class);
        assertEquals("community_accounts", table.getName());
        assertEquals(List.of("user_id"), columnNames(table.getUniqueKey("uk_community_account_user").getColumns()));
        assertTrue(columnNames(table).containsAll(Set.of(
                "id", "user_id", "accepted_terms_version", "terms_accepted_at", "created_at", "updated_at")));
        assertFalse(columnNames(table).contains("email"));
        assertFalse(columnNames(table).contains("password"));
        assertFalse(columnNames(table).contains("username"));
        assertFalse(columnNames(table).contains("full_name"));
        assertReferences(table, "user_id", "users");
        assertEquals(GenerationType.IDENTITY, idStrategy(CommunityAccount.class));
        assertEquals(OneToOne.class, association(CommunityAccount.class, "user"));
    }

    @Test
    void communityIdentityIsNotAJobRow() {
        Table table = table(Community.class);
        assertEquals("communities", table.getName());
        assertEquals(
                List.of("canonical_posting_key"),
                columnNames(table.getUniqueKey("uk_community_canonical_posting_key").getColumns()));
        assertEquals(List.of("community_name"), columnNames(table.getUniqueKey("uk_community_name").getColumns()));
        Column key = column(table, "canonical_posting_key");
        assertEquals(64L, key.getLength());
        assertFalse(key.isNullable());
        assertFalse(jpaColumn(Community.class, "canonicalPostingKey").updatable());
        assertFalse(jpaColumn(Community.class, "communityName").updatable());
        assertFalse(columnNames(table).contains("job_id"));
        assertFalse(columnNames(table).contains("salary"));
        assertFalse(columnNames(table).contains("notes"));
        assertFalse(columnNames(table).contains("resume"));
        assertFalse(columnNames(table).contains("email"));
        assertReferences(table, "created_by_user_id", "users");
        assertTrue(columnNames(table).containsAll(Set.of(
                "title", "company", "location", "description", "created_by_user_id")));
    }

    @Test
    void normalizedAliasCannotBelongToTwoCommunities() {
        Table table = table(CommunityUrlAlias.class);
        assertEquals("community_url_aliases", table.getName());
        assertEquals(
                List.of("normalized_url_hash"),
                columnNames(table.getUniqueKey("uk_community_url_alias_hash").getColumns()));
        assertEquals(64L, column(table, "normalized_url_hash").getLength());
        assertEquals(2000L, column(table, "normalized_url").getLength());
        assertEquals(2000L, column(table, "original_url").getLength());
        assertEquals(
                List.of("community_id"),
                columnNames(table.getIndex("idx_community_url_alias_community").getColumns()));
        assertReferences(table, "community_id", "communities");
        assertFalse(columnNames(table).contains("job_id"));
    }

    @Test
    void membershipIsUniquePerUserSoRejoinUpdatesTheSameRow() {
        Table table = table(CommunityMembership.class);
        assertEquals("community_memberships", table.getName());
        assertEquals(
                List.of("community_id", "user_id"),
                columnNames(table.getUniqueKey("uk_community_membership_community_user").getColumns()));
        assertEquals(
                List.of("user_id", "membership_status"),
                columnNames(table.getIndex("idx_community_membership_user_status").getColumns()));
        assertEquals(
                List.of("community_id", "membership_status"),
                columnNames(table.getIndex("idx_community_membership_community_status").getColumns()));
        assertReferences(table, "community_id", "communities");
        assertReferences(table, "user_id", "users");
        assertFalse(column(table, "joined_at").isNullable());
        assertTrue(column(table, "left_at").isNullable());

        CheckConstraint check = table.getChecks().stream()
                .filter(candidate -> "ck_community_membership_status_left_at".equals(candidate.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(check.getConstraint().contains("membership_status = 'ACTIVE'"));
        assertTrue(check.getConstraint().contains("left_at IS NULL"));
        assertTrue(check.getConstraint().contains("membership_status = 'LEFT'"));
        assertEquals(Set.of("ACTIVE", "LEFT"),
                Set.of(MembershipStatus.values()).stream().map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void messageHistoryIndexIsCommunityThenTimestampThenId() {
        Table table = table(CommunityMessage.class);
        assertEquals("community_messages", table.getName());
        assertEquals(
                List.of("community_id", "created_at", "id"),
                columnNames(table.getIndex("idx_community_message_history").getColumns()));
        assertReferences(table, "community_id", "communities");
        assertReferences(table, "sender_user_id", "users");
        assertFalse(column(table, "content").isNullable());
        assertFalse(columnNames(table).contains("email"));
        assertFalse(columnNames(table).contains("username"));
    }

    @Test
    void repositoryQueriesMatchTheEntityFields() {
        new PartTree("findByUserId", CommunityAccount.class);
        new PartTree("existsByUserId", CommunityAccount.class);
        new PartTree("findByCanonicalPostingKey", Community.class);
        new PartTree("existsByCanonicalPostingKey", Community.class);
        new PartTree("existsByCommunityName", Community.class);
        new PartTree("findByNormalizedUrlHash", CommunityUrlAlias.class);
        new PartTree("existsByNormalizedUrlHash", CommunityUrlAlias.class);
        new PartTree("findByCommunityId", CommunityUrlAlias.class);
        new PartTree("findByCommunityIdAndUserId", CommunityMembership.class);
        new PartTree("existsByCommunityIdAndUserId", CommunityMembership.class);
        new PartTree("countByCommunityIdAndMembershipStatus", CommunityMembership.class);
        new PartTree("findByUserIdAndMembershipStatusOrderByJoinedAtDescIdDesc", CommunityMembership.class);
        new PartTree("findByCommunityIdAndMembershipStatusOrderByJoinedAtAscIdAsc", CommunityMembership.class);
        new PartTree("findByCommunityIdOrderByCreatedAtDescIdDesc", CommunityMessage.class);
        assertNotNull(CommunityAccountRepository.class);
        assertNotNull(CommunityRepository.class);
        assertNotNull(CommunityUrlAliasRepository.class);
        assertNotNull(CommunityMembershipRepository.class);
        assertNotNull(CommunityMessageRepository.class);
    }

    private static Table table(Class<?> entity) {
        PersistentClass binding = metadata.getEntityBinding(entity.getName());
        assertNotNull(binding, entity.getName());
        return binding.getTable();
    }

    private static List<String> columnNames(org.hibernate.mapping.Index index) {
        assertNotNull(index);
        return columnNames(index.getColumns());
    }

    private static List<String> columnNames(List<Column> columns) {
        return columns.stream()
                .map(column -> column.getName().replace("`", "").toLowerCase(Locale.ROOT))
                .toList();
    }

    private static Set<String> columnNames(Table table) {
        return table.getColumns().stream()
                .map(column -> column.getName().replace("`", "").toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static Column column(Table table, String name) {
        return table.getColumns().stream()
                .filter(candidate -> name.equalsIgnoreCase(candidate.getName().replace("`", "")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing column " + name + " on " + table.getName()));
    }

    private static void assertReferences(Table table, String columnName, String referencedTable) {
        ForeignKey match = null;
        for (ForeignKey foreignKey : table.getForeignKeyCollection()) {
            boolean usesColumn = foreignKey.getColumns().stream()
                    .anyMatch(column -> columnName.equalsIgnoreCase(column.getName().replace("`", "")));
            if (usesColumn) {
                match = foreignKey;
                break;
            }
        }
        assertNotNull(match, table.getName() + "." + columnName);
        assertEquals(referencedTable, match.getReferencedTable().getName());
    }

    private static jakarta.persistence.Column jpaColumn(Class<?> type, String field) {
        try {
            jakarta.persistence.Column column = type.getDeclaredField(field).getAnnotation(jakarta.persistence.Column.class);
            assertNotNull(column, field);
            return column;
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    private static GenerationType idStrategy(Class<?> entity) {
        try {
            return entity.getDeclaredField("id").getAnnotation(jakarta.persistence.GeneratedValue.class).strategy();
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }

    private static Class<?> association(Class<?> entity, String fieldName) {
        try {
            java.lang.reflect.Field field = entity.getDeclaredField(fieldName);
            if (field.getAnnotation(OneToOne.class) != null) {
                return OneToOne.class;
            }
            if (field.getAnnotation(ManyToOne.class) != null) {
                return ManyToOne.class;
            }
            throw new AssertionError("No association on " + fieldName);
        } catch (NoSuchFieldException ex) {
            throw new AssertionError(ex);
        }
    }
}
