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
package org.neo4j.dummy;

import java.util.Collections;
import java.util.Map;

import org.neo4j.config.Configuration;
import org.neo4j.config.ConfigurationManager;
import org.neo4j.config.MutationPolicy;
import org.neo4j.config.dummy.Age;
import org.neo4j.dummy.config.Name;

class DopeFish
{
    private static class ImmutableConfig implements Name, Age
    {
        String name;
        int age;

        @Override
        public void name( String name )
        {
            this.name = name;
        }

        @Override
        public void age( int age )
        {
            this.age = age;
        }
    }

    private class MutableConfig implements Reconfigured
    {
        @Override
        public MutationPolicy reconfig( boolean value )
        {
            reconfigured = value;
            return MutationPolicy.MUTABLE;
        }
    }

    private class Configurator extends ConfigurationManager
    {
        Configurator( Configuration config )
        {
            super( config );
        }

        public void reconfigure()
        {
            this.update( "stuff", Collections.singletonMap( "reconf", "true" ) );
        }
    }

    private final String name;
    private final Configurator manager;
    private final int age;
    private boolean reconfigured;

    DopeFish( Configuration configuration )
    {
        // extract and transfer the immutable parameters
        ImmutableConfig config = configuration.configure( new ImmutableConfig() );
        this.name = config.name;
        this.age = config.age;
        configuration.configure( new MutableConfig() );
        this.manager = new Configurator( configuration );
    }

    @Override
    public String toString()
    {
        return "DopeFish[name=" + name + ",age=" + age + ",reconfigured=" + reconfigured + "]";
    }

    public static void main( String[] args )
    {
        new DopeFish( new Configuration()
        {
            @Override
            protected void restart()
            {
                System.out.println( "RESTART!" );
            }

            @Override
            protected void initialize( String group, Map<String, String> config )
            {
                if ("dummy".equals( group ))
                {
                    config.put( "name", "Thobe" );
                    config.put( "age", "27" );
                }
                /*
                BufferedReader input = new BufferedReader( new InputStreamReader( System.in ) );
                try
                {
                    for ( ;; )
                    {
                        System.out.println( "Add key for " + group + ":" );
                        String key = input.readLine().trim();
                        if ( "".equals( key ) ) break;
                        System.out.println( "Enter value for " + group + "." + key + ":" );
                        String value = input.readLine().trim();
                        config.put( key, value );
                    }
                }
                catch ( IOException abort )
                {
                    abort.printStackTrace();
                    return;
                }
                */
            }
        } ).run();
    }

    private void run()
    {
        System.out.println( "The " + this + " lives!" );
        manager.reconfigure();
        System.out.println( "The " + this + " lives!" );
    }
}
