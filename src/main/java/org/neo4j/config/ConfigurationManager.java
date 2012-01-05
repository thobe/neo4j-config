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

import java.util.HashMap;
import java.util.Map;

public abstract class ConfigurationManager
{
    private final Configuration config;

    /**
     * Create a new {@link ConfigurationManager}. The passed in {@link Configuration} will be used to configure this
     * {@link ConfigurationManager}.
     *
     * @param config The {@link Configuration} that this {@link ConfigurationManager} will manage.
     */
    public ConfigurationManager( Configuration config )
    {
        ( this.config = config ).addManager( this );
    }

    protected final void update( final String modifiedGroup, Map<String, String> update )
    {
        final Map<String, String> changes = new HashMap<String, String>( update );
        update( new ConfigurationUpdate()
        {
            @Override
            String pop( String group, String name )
            {
                if ( group.equals( modifiedGroup ) )
                {
                    return changes.remove( name );
                }
                return null;
            }

            @Override
            void done()
            {
                // TODO Auto-generated method stub

            }
        } );
    }

    protected final void update( ConfigurationUpdate update )
    {
        config.change( update );
    }

    protected static abstract class ConfigurationUpdate
    {
        private ConfigurationUpdate()
        {
            // limit the subclasses
        }

        abstract String pop( String group, String name );

        /**
         * invoked when all mutable configurations are {@link #pop(String, String) popped} from this
         */
        abstract void done();
    }
}
