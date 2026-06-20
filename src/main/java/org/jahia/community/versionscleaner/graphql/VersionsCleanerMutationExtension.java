package org.jahia.community.versionscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Versions Cleaner mutations")
public class VersionsCleanerMutationExtension {

    private VersionsCleanerMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("versionsCleaner")
    @GraphQLDescription("Versions Cleaner mutation namespace")
    public static VersionsCleanerMutation versionsCleaner() {
        return new VersionsCleanerMutation();
    }
}
