/*
 * Copyright (C) 2014  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.processor;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.mapfish.print.output.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveTask;
import javax.annotation.Nonnull;

/**
 * Represents a graph of the processors dependencies.  The root nodes can execute in parallel but processors with
 * dependencies must wait for their dependencies to complete before execution.
 * <p/>
 * @author jesseeichar on 3/24/14.
 */
public final class ProcessorDependencyGraph {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorDependencyGraph.class);
    private final List<ProcessorGraphNode> roots;

    ProcessorDependencyGraph() {
        this.roots = new ArrayList<ProcessorGraphNode>();
    }

    /**
     * Create a ForkJoinTask for running in a fork join pool.
     *
     * @param values the values to use for getting the required inputs of the processor and putting the output in.
     * @return a task ready to be submitted to a fork join pool.
     */
    public ProcessorGraphForkJoinTask createTask(@Nonnull final Values values) {
        List<String> missingAttributes = Lists.newArrayList();
        for (String attribute : getAllRequiredAttributes()) {
            if (!values.containsKey(attribute)) {
                missingAttributes.add(attribute);
            }
        }

        if (!missingAttributes.isEmpty()) {
            throw new IllegalArgumentException("The following attributes are not in the values object. " + missingAttributes);
        }

        return new ProcessorGraphForkJoinTask(values);
    }

    /**
     * Add a new root node.
     *
     * @param node new root node.
     */
    void addRoot(final ProcessorGraphNode node) {
        this.roots.add(node);
    }

    /**
     * Get all the names of inputs that are required to be in the Values object when this graph is executed.
     */
    public Collection<String> getAllRequiredAttributes() {
        Set<String> requiredInputs = Sets.newHashSet();
        for (ProcessorGraphNode root : this.roots) {
            requiredInputs.addAll(root.getInputMapper().keySet());
        }

        return requiredInputs;
    }

    public List<ProcessorGraphNode> getRoots() {
        return this.roots;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (ProcessorGraphNode root : this.roots) {
            if (this.roots.indexOf(root) != 0) {
                builder.append('\n');
            }
            builder.append("+ ");
            root.toString(builder, 0);
        }
        return builder.toString();
    }

    /**
     * Create a set containing all the processors in the graph.
     */
    public Set<Processor> getAllProcessors() {
        IdentityHashMap<Processor, Void> all = new IdentityHashMap<Processor, Void>();
        for (ProcessorGraphNode root : this.roots) {
            for (Processor p : root.getAllProcessors()) {
                all.put(p, null);
            }
        }
        return all.keySet();
    }

    /**
     * A ForkJoinTask that will create ForkJoinTasks from each root and run each of them.
     */
    public final class ProcessorGraphForkJoinTask extends RecursiveTask<Values> {
        private final ProcessorExecutionContext execContext;

        private ProcessorGraphForkJoinTask(@Nonnull final Values values) {
            this.execContext = new ProcessorExecutionContext(values);
        }

        @Override
        protected Values compute() {
            final ProcessorDependencyGraph graph = ProcessorDependencyGraph.this;

            LOGGER.debug("Starting to execute processor graph: " + graph);
            try {
                List<ProcessorGraphNode.ProcessorNodeForkJoinTask> tasks =
                        new ArrayList<ProcessorGraphNode.ProcessorNodeForkJoinTask>(graph.roots.size());

                // fork all but 1 dependencies (the first will be ran in current thread)
                for (int i = 0; i < graph.roots.size(); i++) {
                    Optional<ProcessorGraphNode.ProcessorNodeForkJoinTask> task = graph.roots.get(i).createTask(this.execContext);
                    if (task.isPresent()) {
                        tasks.add(task.get());
                        if (tasks.size() > 1) {
                            task.get().fork();
                        }
                    }
                }

                if (!tasks.isEmpty()) {
                    // compute one task in current thread so as not to waste threads
                    tasks.get(0).compute();

                    for (ProcessorGraphNode.ProcessorNodeForkJoinTask task : tasks.subList(1, tasks.size())) {
                        task.join();
                    }
                }
            } finally {
                LOGGER.debug("Finished executing processor graph: " + graph);
            }
            return this.execContext.getValues();
        }
    }
}
