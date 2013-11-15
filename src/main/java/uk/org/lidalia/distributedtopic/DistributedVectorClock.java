package uk.org.lidalia.distributedtopic;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;
import static com.google.common.collect.Maps.immutableEntry;
import static java.util.Collections.min;
import static uk.org.lidalia.distributedtopic.Maps2.uniqueIndex;

public class DistributedVectorClock {

    private final NodeId nodeId;
    private final ImmutableSortedMap<NodeId, VectorClock> state;

    public DistributedVectorClock(NodeId nodeId, NodeId... nodeIds) {
        this(nodeId, ImmutableSortedSet.<NodeId>naturalOrder().add(nodeId).add(nodeIds).build());
    }

    public DistributedVectorClock(NodeId nodeId, ImmutableSortedSet<NodeId> nodeIds) {
        this(nodeId, initialStateFor(nodeIds));
    }

    private static ImmutableSortedMap<NodeId, SingleNodeVectorClock> initialStateFor(final ImmutableSortedSet<NodeId> nodeIds) {
        return uniqueIndex(nodeIds, new Function<NodeId, Map.Entry<NodeId, SingleNodeVectorClock>>() {
            @Override
            public Map.Entry<NodeId, SingleNodeVectorClock> apply(NodeId nodeId) {
                return immutableEntry(nodeId, new SingleNodeVectorClock(nodeId, nodeIds));
            }
        });
    }

    private DistributedVectorClock(NodeId nodeId, ImmutableSortedMap<NodeId, VectorClock> state) {
        this.nodeId = checkNotNull(nodeId);
        this.state = checkNotNull(state);
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public ImmutableSortedMap<NodeId, VectorClock> getState() {
        return state;
    }

    public VectorClock getLocalClock() {
        return state.get(nodeId);
    }

    public SingleNodeVectorClock getLowestCommonClock() {
        final ImmutableSet<SingleNodeVectorClock> singleNodeVectorClocks = from(state.values()).transform(new Function<VectorClock, SingleNodeVectorClock>() {
            @Override
            public SingleNodeVectorClock apply(VectorClock input) {
                return input.getLowestCommonClock();
            }
        }).toSet();

        ImmutableSet<NodeId> allNodeIds = from(singleNodeVectorClocks).transformAndConcat(new Function<SingleNodeVectorClock, Iterable<NodeId>>() {
            @Override
            public Iterable<NodeId> apply(SingleNodeVectorClock input) {
                return input.nodeIds();
            }
        }).toSet();


        ImmutableSortedMap<NodeId, Integer> lowestCommonState = Maps2.uniqueIndex(allNodeIds, new Function<NodeId, Map.Entry<NodeId, Integer>>() {
            @Override
            public Map.Entry<NodeId, Integer> apply(NodeId nodeId) {
                Integer minimum = minimumValueFor(nodeId, singleNodeVectorClocks);
                return immutableEntry(nodeId, minimum);
            }
        });
        return new SingleNodeVectorClock(nodeId, lowestCommonState);
    }

    private Integer minimumValueFor(final NodeId nodeId, ImmutableSet<SingleNodeVectorClock> singleNodeVectorClocks) {
        return min(from(singleNodeVectorClocks).transform(new Function<SingleNodeVectorClock, Integer>() {
            @Override
            public Integer apply(SingleNodeVectorClock vectorClock) {
                return vectorClock.sequenceFor(nodeId).or(0);
            }
        }).toSet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DistributedVectorClock that = (DistributedVectorClock) o;

        if (!nodeId.equals(that.nodeId)) return false;
        if (!state.equals(that.state)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = nodeId.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "{"+nodeId+","+state+'}';
    }

    public DistributedVectorClock update(DistributedVectorClock updatedRemoteClock) {
        Map<NodeId, SingleNodeVectorClock> updatedState = new HashMap<>(state);
        updatedState.put(updatedRemoteClock.getNodeId(), updatedRemoteClock);
        updatedState.put(nodeId, getLocalClock().update(updatedRemoteClock.getNodeId(), updatedRemoteClock.sequenceForDefiningNode()));
        return new DistributedVectorClock(nodeId, ImmutableSortedMap.copyOf(updatedState));
    }

    public DistributedVectorClock next() {
        Map<NodeId, SingleNodeVectorClock> updatedState = new HashMap<>(state);
        updatedState.put(nodeId, getLocalClock().next());
        return new DistributedVectorClock(nodeId, ImmutableSortedMap.copyOf(updatedState));
    }

    public DistributedVectorClock add(NodeId otherNode) {
        return update(new SingleNodeVectorClock(otherNode));
    }
}