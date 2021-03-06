/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.test.graql.analytics;

import ai.grakn.GraknGraph;
import ai.grakn.GraknGraphFactory;
import ai.grakn.concept.Concept;
import ai.grakn.concept.ConceptId;
import ai.grakn.concept.Entity;
import ai.grakn.concept.EntityType;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.RoleType;
import ai.grakn.concept.TypeName;
import ai.grakn.exception.GraknValidationException;
import ai.grakn.graph.internal.computer.GraknSparkComputer;
import ai.grakn.graql.analytics.ClusterQuery;
import ai.grakn.graql.analytics.DegreeQuery;
import ai.grakn.graql.analytics.MaxQuery;
import ai.grakn.graql.analytics.MeanQuery;
import ai.grakn.graql.analytics.MedianQuery;
import ai.grakn.graql.analytics.MinQuery;
import ai.grakn.graql.analytics.PathQuery;
import ai.grakn.graql.analytics.SumQuery;
import ai.grakn.graql.internal.analytics.GraknVertexProgram;
import ai.grakn.test.EngineContext;
import ai.grakn.util.Schema;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static ai.grakn.test.GraknTestEnv.usingOrientDB;
import static ai.grakn.test.GraknTestEnv.usingTinker;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class GraqlTest {

    public GraknGraphFactory factory;

    private static final String thing = "thing";
    private static final String anotherThing = "anotherThing";
    private static final String related = "related";

    private String entityId1;
    private String entityId2;
    private String entityId3;
    private String entityId4;
    private String relationId12;
    private String relationId24;

    @ClassRule
    public static final EngineContext context = EngineContext.startInMemoryServer();

    @Before
    public void setUp() {
        // TODO: Make orientdb support analytics
        assumeFalse(usingOrientDB());

        factory = context.factoryWithNewKeyspace();

        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(GraknVertexProgram.class);
        logger.setLevel(Level.DEBUG);
    }

    @After
    public void close() {
        GraknSparkComputer.close();
    }

    @Test
    public void testGraqlCount() throws GraknValidationException, InterruptedException, ExecutionException {
        addOntologyAndEntities();
        try (GraknGraph graph = factory.getGraph()) {
            assertEquals(6L, ((Long) graph.graql().parse("compute count;").execute()).longValue());
            assertEquals(3L, ((Long) graph.graql().parse("compute count in thing, thing;").execute()).longValue());
        }
    }

    @Test
    public void testDegrees() throws Exception {
        // TODO: Fix on TinkerGraphComputer
        assumeFalse(usingTinker());

        addOntologyAndEntities();
        try (GraknGraph graph = factory.getGraph()) {
            Map<Long, Set<String>> degrees = graph.graql().<DegreeQuery>parse("compute degrees;").execute();

            Map<String, Long> correctDegrees = new HashMap<>();
            correctDegrees.put(entityId1, 1L);
            correctDegrees.put(entityId2, 2L);
            correctDegrees.put(entityId3, 0L);
            correctDegrees.put(entityId4, 1L);
            correctDegrees.put(relationId12, 2L);
            correctDegrees.put(relationId24, 2L);

            assertTrue(!degrees.isEmpty());
            degrees.entrySet().forEach(entry -> entry.getValue().forEach(
                    id -> {
                        assertTrue(correctDegrees.containsKey(id));
                        assertEquals(correctDegrees.get(id), entry.getKey());
                    }
            ));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidIdWithAnalytics() {
        try (GraknGraph graph = factory.getGraph()) {
            graph.graql().parse("compute sum of thing;").execute();
        }
    }

    @Test
    public void testStatisticsMethods() throws GraknValidationException {
        // TODO: Fix on TinkerGraphComputer
        assumeFalse(usingTinker());

        try (GraknGraph graph = factory.getGraph()) {
            TypeName resourceTypeId = TypeName.of("my-resource");

            RoleType resourceOwner = graph.putRoleType(Schema.Resource.HAS_RESOURCE_OWNER.getName(resourceTypeId));
            RoleType resourceValue = graph.putRoleType(Schema.Resource.HAS_RESOURCE_VALUE.getName(resourceTypeId));
            RelationType relationType = graph.putRelationType(Schema.Resource.HAS_RESOURCE.getName(resourceTypeId))
                    .hasRole(resourceOwner)
                    .hasRole(resourceValue);

            ResourceType<Long> resource = graph.putResourceType(resourceTypeId, ResourceType.DataType.LONG)
                    .playsRole(resourceValue);
            EntityType thing = graph.putEntityType("thing").playsRole(resourceOwner);
            Entity theResourceOwner = thing.addEntity();

            relationType.addRelation()
                    .putRolePlayer(resourceOwner, theResourceOwner)
                    .putRolePlayer(resourceValue, resource.putResource(1L));
            relationType.addRelation()
                    .putRolePlayer(resourceOwner, theResourceOwner)
                    .putRolePlayer(resourceValue, resource.putResource(2L));
            relationType.addRelation()
                    .putRolePlayer(resourceOwner, theResourceOwner)
                    .putRolePlayer(resourceValue, resource.putResource(3L));

            graph.commitOnClose();
        }

        try (GraknGraph graph = factory.getGraph()) {
            // use graql to compute various statistics
            Optional<? extends Number> result = graph.graql().<SumQuery>parse("compute sum of my-resource;").execute();
            assertEquals(Optional.of(6L), result);
            result = graph.graql().<MinQuery>parse("compute min of my-resource;").execute();
            assertEquals(Optional.of(1L), result);
            result = graph.graql().<MaxQuery>parse("compute max of my-resource;").execute();
            assertEquals(Optional.of(3L), result);
            result = graph.graql().<MeanQuery>parse("compute mean of my-resource;").execute();
            assert result.isPresent();
            assertEquals(2.0, (Double) result.get(), 0.1);
            result = graph.graql().<MedianQuery>parse("compute median of my-resource;").execute();
            assertEquals(Optional.of(2L), result);
        }
    }

    @Test
    public void testConnectedComponents() throws GraknValidationException {
        // TODO: Fix on TinkerGraphComputer
        assumeFalse(usingTinker());

        try (GraknGraph graph = factory.getGraph()) {
            Map<String, Long> sizeMap =
                    graph.graql().<ClusterQuery<Map<String, Long>>>parse("compute cluster;").execute();
            assertTrue(sizeMap.isEmpty());
            Map<String, Set<String>> memberMap =
                    graph.graql().<ClusterQuery<Map<String, Set<String>>>>parse("compute cluster; members;").execute();
            assertTrue(memberMap.isEmpty());
        }
    }

    @Test
    public void testPath() throws GraknValidationException {
        // TODO: Fix on TinkerGraphComputer
        assumeFalse(usingTinker());

        addOntologyAndEntities();

        try (GraknGraph graph = factory.getGraph()) {
            PathQuery query = graph.graql().parse("compute path from '" + entityId1 + "' to '" + entityId2 + "';");

            Optional<List<Concept>> path = query.execute();
            assert path.isPresent();
            List<String> result = path.get().stream().map(Concept::getId).map(ConceptId::getValue).collect(Collectors.toList());

            List<String> expected = Lists.newArrayList(entityId1, relationId12, entityId2);

            assertEquals(expected, result);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNonResourceTypeAsSubgraphForAnalytics() throws GraknValidationException {
        try (GraknGraph graph = factory.getGraph()) {
            graph.putEntityType(thing);
            graph.commitOnClose();
        }

        try (GraknGraph graph = factory.getGraph()) {
            graph.graql().parse("compute sum in thing;").execute();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testErrorWhenNoSubgrapForAnalytics() throws GraknValidationException {
        try (GraknGraph graph = factory.getGraph()) {
            graph.graql().parse("compute sum;").execute();
            graph.graql().parse("compute min;").execute();
            graph.graql().parse("compute max;").execute();
            graph.graql().parse("compute mean;").execute();
            graph.graql().parse("compute std;").execute();
        }
    }

    @Test
    public void testAnalyticsDoesNotCommitByMistake() throws GraknValidationException {
        // TODO: Fix on TinkerGraphComputer
        assumeFalse(usingTinker());
        try (GraknGraph graph = factory.getGraph()) {
            graph.putResourceType("number", ResourceType.DataType.LONG);
            graph.commitOnClose();
        }

        Set<String> analyticsCommands = new HashSet<>(Arrays.asList(
                "compute count;",
                "compute degrees;",
                "compute mean of number;"));

        analyticsCommands.forEach(command -> {
            try (GraknGraph graph = factory.getGraph()) {
                // insert a node but do not commit it
                graph.graql().parse("insert thing sub entity;").execute();
                // use analytics
                graph.graql().parse(command).execute();
            }

            GraknGraph graph = factory.getGraph();
            // see if the node was commited
            assertNull(graph.getEntityType("thing"));
        });
    }

    private void addOntologyAndEntities() throws GraknValidationException {
        try (GraknGraph graph = factory.getGraph()) {
            EntityType entityType1 = graph.putEntityType(thing);
            EntityType entityType2 = graph.putEntityType(anotherThing);

            Entity entity1 = entityType1.addEntity();
            Entity entity2 = entityType1.addEntity();
            Entity entity3 = entityType1.addEntity();
            Entity entity4 = entityType2.addEntity();

            entityId1 = entity1.getId().getValue();
            entityId2 = entity2.getId().getValue();
            entityId3 = entity3.getId().getValue();
            entityId4 = entity4.getId().getValue();

            RoleType role1 = graph.putRoleType("role1");
            RoleType role2 = graph.putRoleType("role2");
            entityType1.playsRole(role1).playsRole(role2);
            entityType2.playsRole(role1).playsRole(role2);
            RelationType relationType = graph.putRelationType(related).hasRole(role1).hasRole(role2);

            relationId12 = relationType.addRelation()
                    .putRolePlayer(role1, entity1)
                    .putRolePlayer(role2, entity2).getId().getValue();
            relationId24 = relationType.addRelation()
                    .putRolePlayer(role1, entity2)
                    .putRolePlayer(role2, entity4).getId().getValue();

            graph.commitOnClose();
        }
        GraknSparkComputer.close();
    }
}
