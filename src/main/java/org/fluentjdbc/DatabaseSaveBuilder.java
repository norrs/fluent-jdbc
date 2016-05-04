package org.fluentjdbc;

import org.fluentjdbc.DatabaseTable.RowMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public abstract class DatabaseSaveBuilder<T> extends DatabaseStatement {

    protected List<String> uniqueKeyFields = new ArrayList<>();
    protected List<Object> uniqueKeyValues = new ArrayList<>();

    protected List<String> fields = new ArrayList<>();
    protected List<Object> values = new ArrayList<>();

    protected final DatabaseTable table;
    protected String idField;

    @Nullable protected T idValue;

    DatabaseSaveBuilder(DatabaseTable table, String idField, @Nullable T id) {
        this.table = table;
        this.idField = idField;
        this.idValue = id;
    }

    public DatabaseSaveBuilder<T> uniqueKey(String fieldName, @Nullable Object fieldValue) {
        uniqueKeyFields.add(fieldName);
        uniqueKeyValues.add(fieldValue);
        return this;
    }

    public DatabaseSaveBuilder<T> setField(String fieldName, @Nullable Object fieldValue) {
        fields.add(fieldName);
        values.add(fieldValue);
        return this;
    }

    @Nullable
    public T execute(Connection connection) {
        T idValue = this.idValue;
        if (idValue != null) {
            Boolean isSame = table.where(idField, this.idValue).singleObject(connection, new RowMapper<Boolean>() {
                @Override
                public Boolean mapRow(DatabaseRow row) throws SQLException {
                    return valuesAreUnchanged(row);
                }
            });
            if (isSame != null && !isSame) {
                update(connection, idValue);
            } else if (isSame == null) {
                insert(connection);
            }
            return idValue;
        } else if (hasUniqueKey()) {
            Boolean isSame = table.whereAll(uniqueKeyFields, uniqueKeyValues).singleObject(connection, new RowMapper<Boolean>() {
                @SuppressWarnings("unchecked")
                @Override
                public Boolean mapRow(DatabaseRow row) throws SQLException {
                    DatabaseSaveBuilder.this.idValue = (T) row.getObject(idField);
                    return valuesAreUnchanged(row);
                }
            });
            idValue = this.idValue;
            if (idValue == null) {
                idValue = insert(connection);
            } else if (isSame != null && !isSame) {
                update(connection, idValue);
            }
            return idValue;
        } else {
            return insert(connection);
        }
    }

    private boolean valuesAreUnchanged(DatabaseRow row) throws SQLException {
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            if (!equal(values.get(i), row.getObject(field))) return false;
        }
        return true;
    }

    private boolean equal(Object o, Object db) {
        return Objects.equals(o, db);
    }

    private boolean hasUniqueKey() {
        if (uniqueKeyFields.isEmpty()) return false;
        for (Object o : uniqueKeyValues) {
            if (o == null) return false;
        }
        return true;
    }

    @Nullable
    protected T insert(Connection connection) {
        return table.insert()
            .setPrimaryKey(idField, idValue)
            .setFields(fields, values)
            .setFields(uniqueKeyFields, uniqueKeyValues)
            .execute(connection);
    }

    private T update(Connection connection, T idValue) {
        table.where("id", idValue)
                .update()
                .setFields(this.fields, this.values)
                .setFields(uniqueKeyFields, uniqueKeyValues)
                .execute(connection);
        return idValue;
    }

}
