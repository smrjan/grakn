package io.mindmaps.graql.internal.analytics;

import io.mindmaps.concept.ResourceType;
import io.mindmaps.util.Schema;
import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.process.computer.KeyValue;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MaxMapReduce extends MindmapsMapReduce<Number> {

    public static final String MEMORY_KEY = "max";
    public static final String SELECTED_DATA_TYPE = "SELECTED_DATA_TYPE";

    private String resourceDataType = null;

    public MaxMapReduce() {
    }

    public MaxMapReduce(Set<String> selectedTypes, Map<String, String> resourceTypes) {
        this.selectedTypes = selectedTypes;
        resourceDataType = resourceTypes.get(selectedTypes.iterator().next());
    }

    @Override
    public void storeState(final Configuration configuration) {
        super.storeState(configuration);
        configuration.addProperty(SELECTED_DATA_TYPE, resourceDataType);
    }

    @Override
    public void loadState(final Graph graph, final Configuration configuration) {
        super.loadState(graph, configuration);
        resourceDataType = configuration.getString(SELECTED_DATA_TYPE);
    }

    @Override
    public void map(final Vertex vertex, final MapEmitter<Serializable, Number> emitter) {
        if (resourceDataType.equals(ResourceType.DataType.LONG.getName())) {
            if (selectedTypes.contains(getVertexType(vertex))) {
                emitter.emit(MEMORY_KEY, vertex.value(Schema.ConceptProperty.VALUE_LONG.name()));
                return;
            }
            emitter.emit(MEMORY_KEY, Long.MIN_VALUE);
        } else {
            if (selectedTypes.contains(getVertexType(vertex))) {
                emitter.emit(MEMORY_KEY, vertex.value(Schema.ConceptProperty.VALUE_DOUBLE.name()));
                return;
            }
            emitter.emit(MEMORY_KEY, Double.MIN_VALUE);
        }
    }

    @Override
    public void reduce(final Serializable key, final Iterator<Number> values,
                       final ReduceEmitter<Serializable, Number> emitter) {
        if (resourceDataType.equals(ResourceType.DataType.LONG.getName())) {
            emitter.emit(key, IteratorUtils.reduce(values, Long.MIN_VALUE,
                    (a, b) -> a.longValue() > b.longValue() ? a : b));
        } else {
            emitter.emit(key, IteratorUtils.reduce(values, Double.MIN_VALUE,
                    (a, b) -> a.doubleValue() > b.doubleValue() ? a : b));
        }
    }

    @Override
    public void combine(final Serializable key, final Iterator<Number> values,
                        final ReduceEmitter<Serializable, Number> emitter) {
        this.reduce(key, values, emitter);
    }

    @Override
    public boolean doStage(Stage stage) {
        return true;
    }

    @Override
    public Map<Serializable, Number> generateFinalResult(Iterator<KeyValue<Serializable, Number>> keyValues) {
        final Map<Serializable, Number> max = new HashMap<>();
        keyValues.forEachRemaining(pair -> max.put(pair.getKey(), pair.getValue()));
        return max;
    }

}