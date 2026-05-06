package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.versionscleaner.CleanCommand;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("VersionsCleanerQueries")
@GraphQLDescription("Versions Cleaner queries")
public class VersionsCleanerQueryExtension {

    private VersionsCleanerQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("versionsCleanerIsRunning")
    @GraphQLDescription("Returns true if a versions clean operation is currently in progress")
    @GraphQLRequiresPermission("admin")
    public static Boolean isRunning() {
        return CleanCommand.isRunning();
    }
}
