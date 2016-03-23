package org.fluentjdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
class DatabaseStatement {
    protected static Logger logger = LoggerFactory.getLogger(DatabaseStatement.class);

    protected int bindParameters(PreparedStatement stmt, List<Object> parameters) throws SQLException {
        return bindParameters(stmt, parameters, 1);
    }

    protected int bindParameters(PreparedStatement stmt, List<Object> parameters, int start) throws SQLException {
        int index = start;
        for (Object parameter : parameters) {
            bindParameter(stmt, index++, parameter);
        }
        return index;
    }

    protected void bindParameter(PreparedStatement stmt, int index, @Nullable Object parameter) throws SQLException {
        if (parameter instanceof ZonedDateTime) {
            stmt.setTimestamp(index, new Timestamp(((ZonedDateTime)parameter).toInstant().toEpochMilli()));
        } else {
            stmt.setObject(index, parameter);
        }
    }

    protected static List<String> repeat(String string, int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            result.add(string);
        }
        return result;
    }

    protected static String join(String delimiter, Collection<String> strings) {
        StringBuilder result = new StringBuilder();
        for (String s : strings) {
            if (result.length() != 0) {
                result.append(delimiter);
            }
            result.append(s);
        }
        return result.toString();
    }
}
