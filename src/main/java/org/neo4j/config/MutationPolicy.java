/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.config;

import org.neo4j.config.Configuration.Configurator;

public enum MutationPolicy
{
    IMMUTABLE( /*don't care*/true )
    {
        @Override
        void apply( Configuration config, Object target, Configuration.Configurator configurator )
        {
            // do nothing
        }
    },
    MUTABLE( false ),
    MUTABLE_ON_RESTART( true ),
    CREATION_ONLY( /*not even a restart will change this*/false )
    {
        @Override
        void apply( Configuration config, Object target, Configuration.Configurator configurator )
        {
            // do nothing
        }
    };
    static final MutationPolicy ON_NULL = IMMUTABLE;
    private final boolean requireRestart;

    private MutationPolicy( boolean requireRestart )
    {
        this.requireRestart = requireRestart;
    }

    void apply( Configuration config, Object target, Configuration.Configurator configurator )
    {
        config.addMutationListener( new MutationListener( target, configurator ) );
    }

    class MutationListener
    {
        private final Object target;
        final Configurator configurator;

        private MutationListener( Object target, Configurator configurator )
        {
            this.target = target;
            this.configurator = configurator;
        }

        boolean requireRestart()
        {
            return requireRestart;
        }

        void update( Configuration config )
        { // TODO: this isn't invoked anywhere
            configurator.update( config, target );
        }
    }
}
