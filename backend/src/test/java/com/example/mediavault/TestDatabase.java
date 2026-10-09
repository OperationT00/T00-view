package com.example.mediavault;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared fixture reset, restricted to the isolated integration-test schema. */
public final class TestDatabase {
    private TestDatabase() {}
    public static void resetCatalog(JdbcTemplate jdbc) {
        if(!"media_vault_test".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("Tests require isolated media_vault_test database");
        jdbc.update("DELETE FROM legacy_facet_mapping");
        jdbc.update("DELETE FROM asset_location");jdbc.update("DELETE FROM asset_tag");jdbc.update("DELETE FROM asset");jdbc.update("DELETE FROM storage_source");
        while(jdbc.queryForObject("SELECT COUNT(*) FROM tag",Long.class)>0) {
            List<Long> leaves=jdbc.queryForList("SELECT t.id FROM tag t LEFT JOIN tag c ON c.parent_id=t.id WHERE c.id IS NULL",Long.class);
            if(leaves.isEmpty())throw new IllegalStateException("Cyclic test fixture");
            leaves.forEach(id->jdbc.update("DELETE FROM tag WHERE id=?",id));
        }
        for(String table:List.of("skin","catalog_character","creator_account","creator","clothing_tag","content_type","work_scope"))jdbc.update("DELETE FROM "+table);
    }
}
