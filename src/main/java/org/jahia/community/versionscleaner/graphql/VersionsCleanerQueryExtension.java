package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Versions Cleaner queries")
public class VersionsCleanerQueryExtension {

    private VersionsCleanerQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("versionsCleaner")
    @GraphQLDescription("Versions Cleaner query namespace")
    public static VersionsCleanerQuery versionsCleaner() {
        return new VersionsCleanerQuery();
    }
}
