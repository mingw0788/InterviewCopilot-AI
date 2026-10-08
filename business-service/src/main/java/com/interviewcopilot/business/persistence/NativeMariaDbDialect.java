package com.interviewcopilot.business.persistence;

import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.type.SqlTypes;

import java.sql.Types;

/** MariaDB exposes its JSON alias as LONGTEXT through the MySQL JDBC driver. */
public final class NativeMariaDbDialect extends MariaDBDialect {
    @Override
    public boolean equivalentTypes(int first, int second) {
        return super.equivalentTypes(first, second)
                || (first == SqlTypes.JSON && second == Types.LONGVARCHAR)
                || (second == SqlTypes.JSON && first == Types.LONGVARCHAR);
    }
}
