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

package org.mapfish.print.config;

import org.mapfish.print.attribute.AbstractAttribute;
import org.mapfish.print.json.PJsonObject;

import static org.junit.Assert.assertNotNull;

/**
 * Attribute that needs the configuration object injected.
 *
 * @author jesseeichar on 3/25/14.
 */
public class AttributeWithConfigurationInjection extends AbstractAttribute<Integer> implements HasConfiguration {

    private Configuration configuration;

    public void assertInjected() {
        assertNotNull(configuration);
    }

    @Override
    public void setConfiguration(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected String getType() {
        return "attWithConfigInjection";
    }

    @Override
    public Integer getValue(PJsonObject values, String name) {
        return null;
    }
}
