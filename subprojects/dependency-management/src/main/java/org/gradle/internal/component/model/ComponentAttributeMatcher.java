/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.component.model;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.CompatibilityRuleChainInternal;
import org.gradle.api.internal.attributes.DisambiguationRuleChainInternal;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentAttributeMatcher {

    private static final LoadingCache<Key, BitSet> CACHE = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .recordStats()
        .build(new CacheLoader<Key, BitSet>() {
            @Override
            public BitSet load(Key key) throws Exception {
                ComponentAttributeMatcher matcher = new ComponentAttributeMatcher(key.consumerAttributeSchema, key.producerAttributeSchema, key.candidatesToAttributes, key.consumerAttributesContainer, key.attributesToConsider);
                BitSet result = new BitSet(key.candidatesToAttributes.size());
                List<? extends HasAttributes> matches = matcher.getMatches();
                int i = 0;
                for (HasAttributes candidate : key.candidatesToAttributes) {
                    result.set(i++, matches.contains(candidate));
                }
                return result;
            }
        });

    private final AttributesSchema consumerAttributeSchema;
    private final AttributesSchema producerAttributeSchema;
    private final Map<HasAttributes, MatchDetails> matchDetails = Maps.newHashMap();
    private final AttributeContainer consumerAttributesContainer;
    private final AttributeContainer attributesToConsider;


    public static List<? extends HasAttributes> getMatches(AttributesSchema consumerAttributeSchema,
                                                           AttributesSchema producerAttributeSchema,
                                                           Iterable<HasAttributes> candidates, //configAttributes + artifactAttributes
                                                           AttributeContainer consumerAttributesContainer,
                                                           AttributeContainer attributesToConsider) {
        BitSet cached = CACHE.getUnchecked(new Key(consumerAttributeSchema, producerAttributeSchema, candidates, consumerAttributesContainer, attributesToConsider));
        List<HasAttributes> result = Lists.newArrayListWithCapacity(cached.size());
        int i = 0;
        for (HasAttributes candidate : candidates) {
            if (cached.get(i++)) {
                result.add(candidate);
            }
        }
        //System.err.println(CACHE.stats());
        return result;
    }

    public ComponentAttributeMatcher(AttributesSchema consumerAttributeSchema, AttributesSchema producerAttributeSchema,
                                     Iterable<HasAttributes> candidates, //configAttributes + artifactAttributes
                                     AttributeContainer consumerAttributesContainer,
                                     AttributeContainer attributesToConsider) {
        this.consumerAttributeSchema = consumerAttributeSchema;
        this.producerAttributeSchema = producerAttributeSchema;
        for (HasAttributes cand : candidates) {
            if (!cand.getAttributes().isEmpty()) {
                matchDetails.put(cand, new MatchDetails());
            }
        }
        this.consumerAttributesContainer = consumerAttributesContainer;
        this.attributesToConsider = attributesToConsider;
        doMatch();
    }

    private void doMatch() {
        Set<Attribute<Object>> requestedAttributes = Cast.uncheckedCast(consumerAttributesContainer.keySet());
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            HasAttributes key = entry.getKey();
            MatchDetails details = entry.getValue();
            AttributeContainer producerAttributesContainer = key.getAttributes();
            Set<Attribute<Object>> dependencyAttributes = Cast.uncheckedCast(producerAttributesContainer.keySet());
            Set<Attribute<Object>> filter = attributesToConsider != null ? Cast.<Set<Attribute<Object>>>uncheckedCast(attributesToConsider.keySet()) : null;
            Set<Attribute<Object>> allAttributes = filter != null ? filter : Sets.union(requestedAttributes, dependencyAttributes);
            for (Attribute<Object> attribute : allAttributes) {
                AttributeValue<Object> consumerValue = attributeValue(attribute, consumerAttributeSchema, consumerAttributesContainer);
                AttributeValue<Object> producerValue = attributeValue(attribute, producerAttributeSchema, producerAttributesContainer);
                details.update(attribute, consumerAttributeSchema, producerAttributeSchema, consumerValue, producerValue);
            }
        }
    }

    private static AttributeValue<Object> attributeValue(Attribute<Object> attribute, AttributesSchema schema, AttributeContainer container) {
        AttributeValue<Object> attributeValue;
        if (schema.hasAttribute(attribute)) {
            attributeValue = container.contains(attribute) ? AttributeValue.of(container.getAttribute(attribute)) : AttributeValue.missing();
        } else {
            attributeValue = AttributeValue.unknown();
        }
        return attributeValue;
    }

    public List<? extends HasAttributes> getMatches() {
        List<HasAttributes> matchs = new ArrayList<HasAttributes>(1);
        for (Map.Entry<HasAttributes, MatchDetails> entry : matchDetails.entrySet()) {
            MatchDetails details = entry.getValue();
            if (details.compatible) {
                matchs.add(entry.getKey());
            }
        }
        return disambiguateUsingClosestMatch(matchs);
    }

    private List<HasAttributes> disambiguateUsingClosestMatch(List<HasAttributes> matchs) {
        if (matchs.size() > 1) {
            return selectClosestMatches(matchs);
        }
        return matchs;
    }

    private List<HasAttributes> selectClosestMatches(List<HasAttributes> matchs) {
        // if there's more than one compatible match, prefer the closest. However there's a catch.
        // We need to look at all candidates globally, and select the closest match for each attribute
        // then see if there's a non-empty intersection.
        List<HasAttributes> remainingMatches = Lists.newArrayList(matchs);
        List<HasAttributes> best = Lists.newArrayListWithCapacity(matchs.size());
        final ListMultimap<Object, HasAttributes> candidatesByValue = ArrayListMultimap.create();
        Set<Attribute<?>> allAttributes = Sets.newHashSet();
        for (MatchDetails details : matchDetails.values()) {
            allAttributes.addAll(details.matchesByAttribute.keySet());
        }
        for (Attribute<?> attribute : allAttributes) {
            for (HasAttributes match : matchs) {
                Map<Attribute<Object>, Object> matchedAttributes = matchDetails.get(match).matchesByAttribute;
                Object val = matchedAttributes.get(attribute);
                candidatesByValue.put(val, match);
            }
            AttributesSchema schemaToUse = consumerAttributeSchema.hasAttribute(attribute) ? consumerAttributeSchema : producerAttributeSchema;
            disambiguate(remainingMatches, candidatesByValue, schemaToUse.getMatchingStrategy(attribute), best);
            if (remainingMatches.isEmpty()) {
                // the intersection is empty, so we cannot choose
                return matchs;
            }
            candidatesByValue.clear();
            best.clear();
        }
        if (!remainingMatches.isEmpty()) {
            // there's a subset (or not) of best matches
            return remainingMatches;
        }
        return null;
    }

    private static void disambiguate(List<HasAttributes> remainingMatches,
                                     ListMultimap<Object, HasAttributes> candidatesByValue,
                                     AttributeMatchingStrategy<?> matchingStrategy,
                                     List<HasAttributes> best) {
        if (candidatesByValue.isEmpty()) {
            // missing or unknown
            return;
        }
        AttributeMatchingStrategy<Object> ms = Cast.uncheckedCast(matchingStrategy);
        MultipleCandidatesDetails<Object> details = new CandidateDetails(candidatesByValue, best);
        DisambiguationRuleChainInternal<Object> disambiguationRules = (DisambiguationRuleChainInternal<Object>) ms.getDisambiguationRules();
        disambiguationRules.execute(details);
        remainingMatches.retainAll(best);
    }

    private static class MatchDetails {
        private final Map<Attribute<Object>, Object> matchesByAttribute = Maps.newHashMap();

        private boolean compatible = true;

        private void update(final Attribute<Object> attribute, final AttributesSchema consumerSchema, final AttributesSchema producerSchema, final AttributeValue<Object> consumerValue, final AttributeValue<Object> producerValue) {
            AttributesSchema schemaToUse = consumerSchema;
            boolean missingOrUnknown = false;
            if (consumerValue.isUnknown() || consumerValue.isMissing()) {
                // We need to use the producer schema in this case
                schemaToUse = producerSchema;
                missingOrUnknown = true;
            } else if (producerValue.isUnknown() || producerValue.isMissing()) {
                missingOrUnknown = true;
            }
            AttributeMatchingStrategy<Object> strategy = schemaToUse.getMatchingStrategy(attribute);
            CompatibilityRuleChainInternal<Object> compatibilityRules = (CompatibilityRuleChainInternal<Object>) strategy.getCompatibilityRules();
            if (missingOrUnknown) {
                if (compatibilityRules.isCompatibleWhenMissing()) {
                    if (producerValue.isPresent()) {
                        matchesByAttribute.put(attribute, producerValue.get());
                    }
                } else {
                    compatible = false;
                }
                return;
            }
            CompatibilityCheckDetails<Object> details = new CompatibilityCheckDetails<Object>() {
                @Override
                public Object getConsumerValue() {
                    return consumerValue.get();
                }

                @Override
                public Object getProducerValue() {
                    return producerValue.get();
                }

                @Override
                public void compatible() {
                    matchesByAttribute.put(attribute, producerValue.get());
                }

                @Override
                public void incompatible() {
                    compatible = false;
                }
            };
            try {
                compatibilityRules.execute(details);
            } catch (Exception ex) {
                throw new GradleException("Unexpected error thrown when trying to match attribute values with " + strategy, ex);
            }
        }
    }

    private static class CandidateDetails implements MultipleCandidatesDetails<Object> {
        private final ListMultimap<Object, HasAttributes> candidatesByValue;
        private final List<HasAttributes> best;

        public CandidateDetails(ListMultimap<Object, HasAttributes> candidatesByValue, List<HasAttributes> best) {
            this.candidatesByValue = candidatesByValue;
            this.best = best;
        }

        @Override
        public Set<Object> getCandidateValues() {
            return candidatesByValue.keySet();
        }

        @Override
        public void closestMatch(Object candidate) {
            List<HasAttributes> hasAttributes = candidatesByValue.get(candidate);
            for (HasAttributes attributes : hasAttributes) {
                best.add(attributes);
            }
        }
    }

    private static class Key {
        private final static Function<HasAttributes, HasAttributes> TO_ATTRIBUTES = new Function<HasAttributes, HasAttributes>() {
            @Override
            public AttributeContainer apply(HasAttributes input) {
                return ((AttributeContainerInternal)input.getAttributes()).asImmutable();
            }
        };
        final AttributesSchema consumerAttributeSchema;
        final AttributesSchema producerAttributeSchema;
        final AttributeContainer consumerAttributesContainer;
        final AttributeContainer attributesToConsider;
        final List<HasAttributes> candidatesToAttributes;

        private Key(AttributesSchema consumerAttributeSchema, AttributesSchema producerAttributeSchema, Iterable<HasAttributes> candidates, AttributeContainer consumerAttributesContainer, AttributeContainer attributesToConsider) {
            this.consumerAttributeSchema = consumerAttributeSchema;
            this.producerAttributeSchema = producerAttributeSchema;
            this.consumerAttributesContainer = consumerAttributesContainer;
            this.attributesToConsider = attributesToConsider;
            this.candidatesToAttributes = Lists.newArrayList(Iterables.transform(candidates, TO_ATTRIBUTES));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key key = (Key) o;
            return Objects.equal(consumerAttributeSchema, key.consumerAttributeSchema) &&
                Objects.equal(producerAttributeSchema, key.producerAttributeSchema) &&
                Objects.equal(candidatesToAttributes, key.candidatesToAttributes) &&
                Objects.equal(consumerAttributesContainer, key.consumerAttributesContainer) &&
                Objects.equal(attributesToConsider, key.attributesToConsider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(consumerAttributeSchema, producerAttributeSchema, candidatesToAttributes, consumerAttributesContainer, attributesToConsider);
        }
    }
}
