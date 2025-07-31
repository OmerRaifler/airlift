/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.airlift.openmetrics.types;

import com.google.common.collect.ImmutableList;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import static java.util.Objects.requireNonNull;

public record CompositeMetric(String metricName, Map<String, String> labels, String help, List<Metric> subMetrics)
        implements Metric
{
    public static CompositeMetric from(String metricName, Object value, Map<String, String> labels, String help)
    {
        requireNonNull(value, "value is null");
        ImmutableList.Builder<Metric> subMetrics = ImmutableList.builder();
        traverseObject(metricName, value, labels, help, subMetrics);
        return new CompositeMetric(metricName, labels, help, subMetrics.build());
    }

    private static void traverseObject(String prefix, Object value, Map<String, String> labels, String help, ImmutableList.Builder<Metric> metrics)
    {
        switch (value) {
            case Number number -> metrics.add(new Gauge(prefix, number.doubleValue(), labels, help));
            case Boolean bool -> metrics.add(new Gauge(prefix, bool ? 1.0 : 0.0, labels, help));
            case CompositeData compositeData -> {
                CompositeType compositeType = compositeData.getCompositeType();
                Set<String> itemNames = compositeType.keySet();
                for (String itemName : itemNames) {
                    Object itemValue = compositeData.get(itemName);
                    traverseObject(prefix + "_" + itemName, itemValue, labels, help, metrics);
                }
            }
            case TabularData tabularData -> {
                TabularType tabularType = tabularData.getTabularType();
                List<String> indexNames = tabularType.getIndexNames();

                Collection<?> values = tabularData.values();
                if (values == null || values.isEmpty()) {
                    return;
                }

                for (Object entry : values) {
                    if (entry instanceof CompositeData compositeData) {
                        Map<String, String> rowLabels = new HashMap<>(labels);
                        for (String indexName : indexNames) {
                            Object indexValue = compositeData.get(indexName);
                            if (indexValue != null) {
                                rowLabels.put(indexName, indexValue.toString());
                            }
                        }

                        CompositeType compositeType = compositeData.getCompositeType();
                        Set<String> itemNames = compositeType.keySet();
                        for (String itemName : itemNames) {
                            if (indexNames.contains(itemName)) {
                                continue;
                            }
                            Object itemValue = compositeData.get(itemName);
                            traverseObject(prefix + "_" + itemName, itemValue, rowLabels, help, metrics);
                        }
                    }
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + value);
        }
    }

    @Override
    public String getMetricExposition()
    {
        StringBuilder exposition = new StringBuilder();
        for (Metric subMetric : subMetrics) {
            exposition.append(subMetric.getMetricExposition());
        }
        return exposition.toString();
    }
}

