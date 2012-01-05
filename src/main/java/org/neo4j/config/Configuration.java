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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.neo4j.config.SimpleParameterType.Conversion;

public abstract class Configuration
{
    public final <T> T configure( T object )
    {
        for ( Class<?> iface : object.getClass().getInterfaces() )
        {
            Configurator configurator = getConfigurator( iface );
            if ( configurator != null ) configurator.configure( this, object );
        }
        return object;
    }

    private final List<ConfigurationManager> managers = new CopyOnWriteArrayList<ConfigurationManager>();

    void addManager( ConfigurationManager manager )
    {
        managers.add( configure( manager ) );
    }

    void change( ConfigurationManager.ConfigurationUpdate update )
    {
        boolean restart = false;
        Map<Key, UpdatedValue> updates = new HashMap<Key, UpdatedValue>();
        for ( MutationPolicy.MutationListener listener : changeListeners )
        {
            Key key = new Key( listener.configurator.group, listener.configurator.name );
            UpdatedValue values = updates.get( key );
            if ( values == null )
            {
                String value = update.pop( key.group, key.name );
                if ( value != null ) updates.put( key, values = new UpdatedValue( value ) );
            }
            if ( values != null )
            {
                values.add( listener );
                restart |= listener.requireRestart();
            }
        }
        update.done();
        for ( Map.Entry<Key, UpdatedValue> change : updates.entrySet() )
        {
            Key key = change.getKey();
            set( key.group, key.name, change.getValue().value );
        }
        if ( restart )
        {
            restart();
        }
        else
        {
            for ( UpdatedValue change : updates.values() )
            {
                change.update( this );
            }
        }
    }

    protected abstract void restart();

    protected abstract void initialize( String group, Map<String, String> config );

    private static class Key
    {
        final String group, name;

        Key( String group, String name )
        {
            this.group = group;
            this.name = name;
        }

        @Override
        public int hashCode()
        {
            return group.hashCode() * 31 | name.hashCode();
        }

        @Override
        public boolean equals( Object obj )
        {
            if ( this == obj ) return true;
            if ( obj instanceof Key )
            {
                Key that = (Key) obj;
                return group.equals( that.group ) && name.equals( that.name );
            }
            return false;
        }
    }

    @SuppressWarnings( "serial" )
    private static class UpdatedValue extends ArrayList<MutationPolicy.MutationListener>
    {
        private final String value;

        UpdatedValue( String value )
        {
            this.value = value;
        }

        void update( Configuration config )
        {
            for ( MutationPolicy.MutationListener listener : this )
            {
                listener.update( config );
            }
        }
    }

    private final List<MutationPolicy.MutationListener> changeListeners = new CopyOnWriteArrayList<MutationPolicy.MutationListener>();

    void addMutationListener( MutationPolicy.MutationListener listener )
    {
        changeListeners.add( listener );
    }

    String get( String group, String name )
    {
        ConfigGroup cfg;
        synchronized ( groups )
        {
            cfg = groups.get( group );
            if ( cfg == null ) groups.put( group, cfg = new ConfigGroup( group, this ) );
        }
        return cfg.get( name );
    }

    void set( String group, String name, String value )
    {
        ConfigGroup cfg;
        synchronized ( groups )
        {
            cfg = groups.get( group );
        }
        if ( cfg == null ) throw new IllegalStateException( "Updating a value for an unknown configuration group." );
        cfg.set( name, value );
    }

    private final Map<String, ConfigGroup> groups = new HashMap<String, ConfigGroup>();

    private static class ConfigGroup
    {
        private final Map<String, String> config = new HashMap<String, String>()
        {
            @Override
            public String put( String key, String value )
            {
                return super.put( key.toLowerCase(), value );
            }

            @Override
            public String get( Object key )
            {
                if ( key instanceof String )
                {
                    key = ( (String) key ).toLowerCase();
                }
                return super.get( key );
            }
        };

        ConfigGroup( String group, Configuration configuration )
        {
            synchronized ( config )
            {
                configuration.initialize( group, config );
            }
        }

        String get( String key )
        {
            synchronized ( config )
            {
                return config.get( key );
            }
        }

        void set( String key, String value )
        {
            synchronized ( config )
            {
                config.put( key, value );
            }
        }
    }

    private static final String[] CONFIG = {"configuration","config"};
    private final Map<Class<?>, Configurator> configurators = new HashMap<Class<?>, Configurator>();

    private Configurator getConfigurator( Class<?> iface )
    {
        Configurator configurator;
        synchronized ( configurators )
        {
            configurator = configurators.get( iface );
        }
        if ( configurator == null )
        {
            Parameter param = iface.getAnnotation( Parameter.class );
            if ( param == null ) return null;
            configurator = createConfigurator( iface, param );
            synchronized ( configurators )
            {
                Configurator other = configurators.get( iface );
                if ( other != null ) return other;
                configurators.put( iface, configurator );
            }
        }
        return configurator;
    }

    private static Configurator createConfigurator( Class<?> iface, Parameter param )
    {
        Method[] methods = iface.getDeclaredMethods();
        if ( methods.length != 1 )
            throw new IllegalArgumentException( "Configuration interfaces must define exactly one method. "
                                                + iface.getName() + " defines " + methods.length + "." );
        Method method = methods[0];
        String group = param.group().toLowerCase();
        if ( "".equals( group ) )
        {
            group = iface.getPackage().getName().toLowerCase();
            for ( final String config : CONFIG )
            {
                int idx = group.indexOf( config );
                if ( idx >= 0 )
                {
                    if ( group.length() - idx == config.length() )
                    {
                        if ( idx > 0 && group.charAt( idx - 1 ) == '.' )
                        {
                            int lix = group.lastIndexOf( '.', idx - 2 );
                            group = group.substring( lix + 1, idx - 1 );
                        }
                        else
                        {
                            group = "";
                        }
                    }
                    else if ( group.charAt( idx + config.length() ) == '.' )
                    {
                        group = group.substring( idx + config.length() + 1 );
                    }
                    break;
                }
            }
        }
        if ( "".equals( group ) )
            throw new IllegalArgumentException( "Could not determine configuration group for " + iface.getName() );
        String name = param.name();
        if ( "".equals( name ) )
        {
            name = method.getName();
            if ( name.startsWith( "set" ) )
            {
                name = name.substring( 3 );
            }
            else
            {
                name = iface.getSimpleName();
            }
        }
        return Configurator.create( method, group, name );
    }

    static abstract class Configurator
    {
        final Method method;
        private final String group, name;
        private final ResultHandler handler;

        Configurator( Method method, String group, String name, ResultHandler handler )
        {
            this.method = method;
            this.group = group;
            this.name = name;
            this.handler = handler;
        }

        static Configurator create( Method method, String group, String name )
        {
            Parameter.Type type = method.getAnnotation( Parameter.Type.class );
            Parameter.TypeConversion conversion = method.getAnnotation( Parameter.TypeConversion.class );
            ResultHandler result = ResultHandler.get( method.getReturnType() );
            if ( type != null )
            {
                if ( conversion != null )
                    throw new IllegalArgumentException( "Configuration interface method may not declare both "
                                                        + "@Parameter.Type and @Parameter.TypeConversion." );
                Conversion converter = type.value().conversionFor( method.getParameterTypes() );
                String defaultInput = type.defaultValue();
                try
                {
                    if ( defaultInput == Parameter.Type.class.getDeclaredMethod( "defaultValue" ).getDefaultValue() )
                    {
                        defaultInput = converter.defaultInput();
                    }
                }
                catch ( NoSuchMethodException ignored )
                {
                    // ignore
                }
                return new SimpleConfigurator( converter, defaultInput, method, group, name, result );
            }
            else if ( conversion != null )
            {
                try
                {
                    return new CustomConfigurator( conversion.value().newInstance(), method, group, name, result );
                }
                catch ( InstantiationException cause )
                {
                    throw new IllegalArgumentException( "Converter not instantiatable", cause );
                }
                catch ( IllegalAccessException cause )
                {
                    throw new IllegalArgumentException( "Converter constructor not accessible", cause );
                }
            }
            else
            { // both are null => implicit simple type
                SimpleParameterType.Conversion converter = SimpleParameterType.lookupConversion( method.getGenericParameterTypes() );
                return new SimpleConfigurator( converter, converter.defaultInput(), method, group, name, result );
            }
        }

        void update( Configuration config, Object target )
        {
            apply( config, target, false );
        }

        void configure( Configuration config, Object target )
        {
            apply( config, target, true );
        }

        private void apply( Configuration config, Object target, boolean applyHandler )
        {
            Object result;
            String value = config.get( group, name );
            try
            {
                result = method.invoke( target, value == null ? defaultValue() : convert( value ) );
            }
            catch ( IllegalAccessException e )
            {
                throw new Error( e );
            }
            catch ( InvocationTargetException e )
            {
                throw (RuntimeException) e.getTargetException(); // TODO: replace with safeCast()
            }
            if ( applyHandler ) handler.handle( this, target, config, result );
        }

        abstract Object[] convert( String input );

        abstract Object[] defaultValue();
    }

    private enum ResultHandler
    {
        IGNORE( void.class, Void.class )
        {
            @Override
            void handle( Configurator configurator, Object target, Configuration config, Object result )
            {
                // do nothing
            }
        },
        MUTATION( MutationPolicy.class )
        {
            @Override
            void handle( Configurator configurator, Object target, Configuration config, Object result )
            {
                if ( result == null ) result = MutationPolicy.ON_NULL;
                ( (MutationPolicy) result ).apply( config, target, configurator );
            }
        },
        ;

        abstract void handle( Configurator configurator, Object target, Configuration config, Object result );

        static ResultHandler get( Class<?> resultType )
        {
            ResultHandler handler = handlers.get( resultType );
            if ( handler == null )
            {
                System.err.println( "WARNING: ignoring results of type " + resultType ); // TODO: replace with logging
                handler = IGNORE;
            }
            return handler;
        }

        private final Class<?>[] types;

        ResultHandler( Class<?>... types )
        {
            this.types = types;
        }

        private static final Map<Class<?>, ResultHandler> handlers;
        static
        {
            Map<Class<?>, ResultHandler> all = new HashMap<Class<?>, ResultHandler>();
            for ( ResultHandler handler : values() )
            {
                for ( Class<?> type : handler.types )
                {
                    all.put( type, handler );
                }
            }
            handlers = Collections.unmodifiableMap( all );
        }
    }

    private static class SimpleConfigurator extends Configurator
    {
        private final SimpleParameterType.Conversion conversion;
        private final String defaultInput;

        SimpleConfigurator( SimpleParameterType.Conversion conversion, String defaultInput, Method method,
                            String group, String name, ResultHandler handler )
        {
            super( method, group, name, handler );
            this.conversion = conversion;
            this.defaultInput = defaultInput;
        }

        @Override
        Object[] convert( String input )
        {
            return conversion.performOn( input, method );
        }

        @Override
        Object[] defaultValue()
        {
            return conversion.performOn( defaultInput, method );
        }
    }

    private static class CustomConfigurator extends Configurator
    {
        private final ParameterConverter<?> converter;

        CustomConfigurator( ParameterConverter<?> converter, Method method, String group, String name,
                            ResultHandler handler )
        {
            super( method, group, name, handler );
            this.converter = converter;
        }

        @Override
        Object[] convert( String input )
        {
            return new Object[] { converter.convert( input ) };
        }

        @Override
        Object[] defaultValue()
        {
            return new Object[] { converter.defaultValue() };
        }
    }
}
