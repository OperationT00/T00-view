package com.example.mediavault.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GuardService {
    private final JdbcTemplate jdbc;
    public GuardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void lockTags() { lock("TAG_TREE"); }
    public void lockFacets() { lock("FACET_CATALOG"); }
    public void lockAdmins() { lock("ADMIN_INVARIANT"); }
    private void lock(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Guard requires a transaction");
        jdbc.queryForObject("SELECT guard_key FROM app_guard WHERE guard_key = ? FOR UPDATE",String.class,key);
    }
}
