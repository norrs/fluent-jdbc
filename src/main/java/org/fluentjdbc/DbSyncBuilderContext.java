package org.fluentjdbc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DbSyncBuilderContext<T> {
    private final DbTableContext table;
    private final List<T> entities;
    private List<String> uniqueFields = new ArrayList<>();
    private List<Function<T, Object>> uniqueValueFunctions = new ArrayList<>();
    private List<String> updatedFields = new ArrayList<>();
    private List<Function<T, Object>> updatedValueFunctions = new ArrayList<>();
    private Map<List<Object>, Map<String, Object>> existingRows;
    private Map<List<Object>, Map<String, Object>> updated;
    private EnumMap<DatabaseSaveResult.SaveStatus, Integer> status = new EnumMap<>(DatabaseSaveResult.SaveStatus.class);

    public DbSyncBuilderContext(DbTableContext table, List<T> entities) {
        this.table = table;
        this.entities = entities;
        Stream.of(DatabaseSaveResult.SaveStatus.values()).forEach(v -> status.put(v, 0));
    }

    public DbSyncBuilderContext<T> unique(String field, Function<T, Object> valueFunction) {
        uniqueFields.add(field);
        uniqueValueFunctions.add(valueFunction);
        return this;
    }

    public DbSyncBuilderContext<T> field(String field, Function<T, Object> valueFunction) {
        updatedFields.add(field);
        updatedValueFunctions.add(valueFunction);
        return this;
    }

    public DbSyncBuilderContext<T> cacheExisting() {
        Map<List<Object>, Map<String, Object>> existing = new HashMap<>();
        table.query().forEach(row -> {
            List<Object> key = new ArrayList<>();
            for (String field : uniqueFields) {
                key.add(row.getObject(field));
            }
            HashMap<String, Object> result = new HashMap<>();
            for (String field : updatedFields) {
                result.put(field, row.getObject(field));
            }
            existing.put(key, result);
        });
        this.existingRows = existing;

        Map<List<Object>, Map<String, Object>> updated = new HashMap<>();
        entities.forEach(entity -> {
            List<Object> keys = uniqueValueFunctions.stream().map(f -> f.apply(entity)).collect(Collectors.toList());
            HashMap<String, Object> result = new HashMap<>();
            for (int i = 0; i < updatedFields.size(); i++) {
                String field = updatedFields.get(i);
                result.put(updatedFields.get(i), updatedValueFunctions.get(i).apply(entity));
            }
            updated.put(keys, result);
        });
        this.updated = updated;

        return this;
    }

    public DbSyncBuilderContext<T> deleteExtras() {
        this.existingRows.keySet().stream()
                .filter(key -> !updated.containsKey(key))
                .peek(entry -> addStatus(DatabaseSaveResult.SaveStatus.DELETED))
                .forEach(key -> table.whereAll(uniqueFields, key).executeDelete());
        return this;
    }

    public DbSyncBuilderContext<T> insertMissing() {
        this.updated.entrySet().stream()
                .filter(entry -> !existingRows.containsKey(entry.getKey()))
                .peek(entry -> addStatus(DatabaseSaveResult.SaveStatus.INSERTED))
                .forEach(entry -> table.insert()
                        .setFields(uniqueFields, entry.getKey())
                        .setFields(entry.getValue().keySet(), entry.getValue().values())
                        .execute());
        return this;
    }

    public DbSyncBuilderContext<T> updateDiffering() {
        this.updated.entrySet().stream()
                .filter(entry -> existingRows.containsKey(entry.getKey()))
                .forEach(entry -> {
                    if (valuesEqual(entry.getKey())) {
                        addStatus(DatabaseSaveResult.SaveStatus.UNCHANGED);
                    } else {
                        addStatus(DatabaseSaveResult.SaveStatus.UPDATED);
                        table.whereAll(uniqueFields, entry.getKey())
                                .update()
                                .setFields(entry.getValue().keySet(), entry.getValue().values())
                                .execute();
                    }
                });
        return this;
    }

    protected boolean valuesEqual(List<Object> key) {
        System.out.println(existingRows.get(key));
        System.out.println(updated.get(key));
        return existingRows.get(key).equals(updated.get(key));
    }

    private void addStatus(DatabaseSaveResult.SaveStatus status) {
        this.status.put(status, this.status.get(status) + 1);
    }

    public EnumMap<DatabaseSaveResult.SaveStatus, Integer> getStatus() {
        return status;
    }
}
