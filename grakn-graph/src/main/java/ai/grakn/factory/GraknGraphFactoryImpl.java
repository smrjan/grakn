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

package ai.grakn.factory;

import ai.grakn.Grakn;
import ai.grakn.GraknComputer;
import ai.grakn.GraknGraph;
import ai.grakn.GraknGraphFactory;
import ai.grakn.exception.GraphRuntimeException;
import ai.grakn.graph.internal.AbstractGraknGraph;
import ai.grakn.util.EngineCommunicator;
import ai.grakn.graph.internal.GraknComputerImpl;
import ai.grakn.util.ErrorMessage;
import ai.grakn.util.REST;
import org.apache.tinkerpop.gremlin.structure.Graph;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import java.util.function.Supplier;

import static ai.grakn.util.REST.Request.GRAPH_CONFIG_PARAM;
import static ai.grakn.util.REST.WebPath.GRAPH_FACTORY_URI;

/**
 * <p>
 *     Builds a Grakn Graph factory
 * </p>
 *
 * <p>
 *     This class facilitates the construction of Grakn Graphs by determining which factory should be built.
 *     It does this by either defaulting to an in memory graph {@link ai.grakn.graph.internal.GraknTinkerGraph} or by
 *     retrieving the factory definition from engine.
 *
 *     The deployer of engine decides on the backend and this class will handle producing the correct graphs.
 * </p>
 *
 * @author fppt
 */
public class GraknGraphFactoryImpl implements GraknGraphFactory {
    private static final String TINKER_GRAPH_COMPUTER = "org.apache.tinkerpop.gremlin.tinkergraph.process.computer.TinkerGraphComputer";
    private static final String COMPUTER = "graph.computer";
    private final String location;
    private final String keyspace;

    //Flags so we don't have to open a graph just to check the count of the transactions
    private boolean graphOpen = false;
    private boolean graphBatchOpen = false;

    public GraknGraphFactoryImpl(String keyspace, String location){
        this.location = location;
        this.keyspace = keyspace;
    }

    /**
     *
     * @return A new or existing grakn graph with the defined name
     */
    @Override
    public GraknGraph getGraph(){
        graphOpen = true;
        return getConfiguredFactory().factory.getGraph(false);
    }

    /**
     *
     * @return A new or existing grakn graph with the defined name connecting to the specified remote location with batch loading enabled
     */
    @Override
    public GraknGraph getGraphBatchLoading(){
        graphBatchOpen = true;
        return getConfiguredFactory().factory.getGraph(true);
    }

    private ConfiguredFactory getConfiguredFactory(){
        return configureGraphFactory(keyspace, location, REST.GraphConfig.DEFAULT);
    }

    /**
     * @return A new or existing grakn graph compute with the defined name
     */
    @Override
    public GraknComputer getGraphComputer() {
        ConfiguredFactory configuredFactory = configureGraphFactory(keyspace, location, REST.GraphConfig.COMPUTER);
        Graph graph = configuredFactory.factory.getTinkerPopGraph(false);
        return new GraknComputerImpl(graph, configuredFactory.graphComputer);
    }

    @Override
    public void close() throws GraphRuntimeException {
        checkClosure(openGraphTxs(), this::getGraph);
        checkClosure(openGraphBatchTxs(), this::getGraphBatchLoading);

        //Close the main graph connections
        try {
            if(graphOpen) ((AbstractGraknGraph)getGraph()).getTinkerPopGraph().close();
            if(graphBatchOpen) ((AbstractGraknGraph)getGraphBatchLoading()).getTinkerPopGraph().close();
        } catch (Exception e) {
            throw new GraphRuntimeException("Could not close graph.", e);
        }
    }
    private void checkClosure(int numOpenTransactions, Supplier<GraknGraph> graphSupplier){
        if(numOpenTransactions > 1){
            GraknGraph graph = graphSupplier.get();
            throw new GraphRuntimeException(ErrorMessage.TRANSACTIONS_OPEN.getMessage(graph, graph.getKeyspace(), numOpenTransactions));
        }
    }


    @Override
    public int openGraphTxs() {
        if(!graphOpen) return 0;
        return ((AbstractGraknGraph)getGraph()).numOpenTx();
    }

    @Override
    public int openGraphBatchTxs() {
        if(!graphBatchOpen) return 0;
        return ((AbstractGraknGraph)getGraphBatchLoading()).numOpenTx();
    }

    /**
     * @param keyspace The keyspace of the graph
     * @param location The of where the graph is stored
     * @param graphType The type of graph to produce, default, batch, or compute
     * @return A new or existing grakn graph factory with the defined name connecting to the specified remote location
     */
    static ConfiguredFactory configureGraphFactory(String keyspace, String location, String graphType){
        if(Grakn.IN_MEMORY.equals(location)){
            return configureGraphFactoryInMemory(keyspace);
        } else {
            return configureGraphFactoryRemote(keyspace, location, graphType);
        }
    }

    /**
     *
     * @param keyspace The keyspace of the graph
     * @param engineUrl The url of engine to get the graph factory config from
     * @param graphType The type of graph to produce, default, batch, or compute
     * @return A new or existing grakn graph factory with the defined name connecting to the specified remote location
     */
    private static ConfiguredFactory configureGraphFactoryRemote(String keyspace, String engineUrl, String graphType){
        try {
            String restFactoryUri = engineUrl + GRAPH_FACTORY_URI + "?" + GRAPH_CONFIG_PARAM + "=" + graphType;

            Properties properties = new Properties();
            properties.load(new StringReader(EngineCommunicator.contactEngine(restFactoryUri, REST.HttpConn.GET_METHOD)));

            String computer = null;
            if(properties.containsKey(COMPUTER)){
                computer = properties.get(COMPUTER).toString();
            }

            return new ConfiguredFactory(properties, computer, FactoryBuilder.getFactory(keyspace, engineUrl, properties));
        } catch (IOException e) {
            throw new IllegalArgumentException(ErrorMessage.CONFIG_NOT_FOUND.getMessage(engineUrl, e.getMessage()));
        }
    }

    /**
     *
     * @param keyspace The keyspace of the graph
     * @return  A new or existing grakn graph factory with the defined name holding the graph in memory
     */
    private static ConfiguredFactory configureGraphFactoryInMemory(String keyspace){
        InternalFactory factory = FactoryBuilder.getGraknGraphFactory(TinkerInternalFactory.class.getName(), keyspace, Grakn.IN_MEMORY, null);
        return new ConfiguredFactory(null, TINKER_GRAPH_COMPUTER, factory);
    }

    static class ConfiguredFactory {
        final Properties properties;
        final String graphComputer;
        final InternalFactory factory;

        ConfiguredFactory(Properties properties, String graphComputer, InternalFactory factory){
            this.properties = properties;
            this.graphComputer = graphComputer;
            this.factory = factory;
        }
    }
}
