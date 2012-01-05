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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum SimpleParameterType
{
    STRING( SimpleParam.STRING, ListParam.STRING ),
    NUMBER( SimpleParam.LONG, SimpleParam.DOUBLE, SimpleParam.INTEGER, SimpleParam.FLOAT, SimpleParam.SHORT,
            SimpleParam.BYTE, ListParam.LONG, ListParam.DOUBLE, ListParam.INTEGER, ListParam.FLOAT, ListParam.SHORT,
            ListParam.BYTE ),
    HEX( SimpleParam.HEX_LONG, SimpleParam.HEX_INT, SimpleParam.HEX_SHORT, SimpleParam.HEX_BYTE, ListParam.HEX_LONG,
            ListParam.HEX_INT, ListParam.HEX_SHORT, ListParam.HEX_BYTE ),
    BOOLEAN( SimpleParam.BOOLEAN, ListParam.BOOLEAN ),
    URI( SimpleParam.URI, SimpleParam.URL, SimpleParam.URI_STRING ),
    HOST_AND_PORT( SimpleParam.HOST_AND_PORT, MultiParam.HOST_AND_PORT ), ;

    interface Conversion
    {
        boolean handles( Class<?>[] params );

        String defaultInput();

        Object[] performOn( String input, Method method );
    }

    private enum SimpleParam implements Conversion
    {
        STRING( null, String.class )
        {
            @Override
            Object convert( String input )
            {
                return input;
            }

            @Override
            String[] boxedArray( String input )
            {
                return input.split( "," );
            }
        },
        BOOLEAN( "false", boolean.class, Boolean.class )
        {
            @Override
            Object convert( String input )
            {
                return Boolean.valueOf( input.trim() );
            }
        },
        BYTE( "0", byte.class, Byte.class )
        {
            @Override
            Object convert( String input )
            {
                return Byte.valueOf( input.trim() );
            }
        },
        HEX_BYTE( "0", byte.class, Byte.class )
        {
            @Override
            Object convert( String input )
            {
                return Byte.valueOf( input.trim(), 16 );
            }
        },
        SHORT( "0", short.class, Short.class )
        {
            @Override
            Object convert( String input )
            {
                return Short.valueOf( input.trim() );
            }
        },
        HEX_SHORT( "0", short.class, Short.class )
        {
            @Override
            Object convert( String input )
            {
                return Short.valueOf( input.trim(), 16 );
            }
        },
        INTEGER( "0", int.class, Integer.class )
        {
            @Override
            Object convert( String input )
            {
                return Integer.valueOf( input.trim() );
            }
        },
        HEX_INT( "0x0", int.class, Integer.class )
        {
            @Override
            Object convert( String input )
            {
                return Integer.valueOf( input.trim(), 16 );
            }
        },
        LONG( "0", long.class, Long.class )
        {
            @Override
            Object convert( String input )
            {
                return Long.valueOf( input.trim() );
            }
        },
        HEX_LONG( "0x0", long.class, Long.class )
        {
            @Override
            Object convert( String input )
            {
                return Long.valueOf( input.trim(), 16 );
            }
        },
        FLOAT( "0", float.class, Float.class )
        {
            @Override
            Object convert( String input )
            {
                return Float.valueOf( input.trim() );
            }
        },
        DOUBLE( "0", double.class, Double.class )
        {
            @Override
            Object convert( String input )
            {
                return Double.valueOf( input.trim() );
            }
        },
        URI( null, java.net.URI.class )
        {
            @Override
            Object convert( String input )
            {
                return java.net.URI.create( input );
            }
        },
        URL( null, java.net.URL.class )
        {
            @Override
            Object convert( String input )
            {
                try
                {
                    return new java.net.URL( input );
                }
                catch ( MalformedURLException cause )
                {
                    throw new IllegalArgumentException( cause );
                }
            }
        },
        URI_STRING( null, String.class )
        {
            @Override
            Object convert( String input )
            {
                return java.net.URI.create( input ).toString();
            }
        },
        HOST_AND_PORT( null, java.net.URI.class )
        {
            @Override
            Object convert( String input )
            {
                Object[] result = MultiParam.HOST_AND_PORT.convert( input );
                try
                {
                    return new java.net.URI( null, null, (String) result[0], ( (Integer) result[1] ).intValue(), null,
                                             null, null );
                }
                catch ( URISyntaxException e )
                {
                    throw new IllegalArgumentException( e );
                }
            }
        },
        ;
        private final String defaultInput;
        private final Class<?>[] options;

        private SimpleParam( String defaultInput, Class<?>... options )
        {
            if ( options.length < 1 || ( options[0].isPrimitive() ? options.length != 2 : false ) )
                throw new LinkageError( "Primitive parameters must have their boxed counterpart as only other option." );
            this.defaultInput = defaultInput;
            this.options = options;
        }

        @Override
        public String defaultInput()
        {
            return defaultInput;
        }

        @Override
        public boolean handles( Class<?>[] params )
        {
            if ( params.length != 1 ) return false;
            Class<?> param = params[0];
            for ( Class<?> option : options )
            {
                if ( option == param ) return true;
            }
            return false;
        }

        @Override
        public Object[] performOn( String input, Method method )
        {
            return new Object[] { convert( input ) };
        }

        abstract Object convert( String input );

        Object primitiveArray( String input )
        {
            Class<?> component = options[0];
            if ( !component.isPrimitive() ) throw new Error( this + " does not represent a primitive type" );
            return array( input, component );
        }

        Object boxedArray( String input )
        {
            return array( input, options[1] );
        }

        private Object array( String input, Class<?> component )
        {
            if ( input == null || "".equals( input = input.trim() ) ) return Array.newInstance( component, 0 );
            String[] parts = input.split( "," );
            Object array = Array.newInstance( component, parts.length );
            for ( int i = 0; i < parts.length; i++ )
            {
                Array.set( array, i, convert( parts[i] ) );
            }
            return array;
        }

        void addTo( Map<Class<?>, Conversion> simples )
        {
            for ( Class<?> type : options )
                if ( simples.get( type ) == null ) simples.put( type, this );
        }
    }

    private enum ListParam implements Conversion
    {
        STRING( SimpleParam.STRING, String[].class ),
        BOOLEAN( SimpleParam.BOOLEAN, boolean[].class, Boolean[].class ),
        BYTE( SimpleParam.BYTE, byte[].class, Byte[].class ),
        HEX_BYTE( SimpleParam.HEX_BYTE, byte[].class, Byte[].class ),
        SHORT( SimpleParam.SHORT, short[].class, Short[].class ),
        HEX_SHORT( SimpleParam.SHORT, short[].class, Short[].class ),
        INTEGER( SimpleParam.INTEGER, int[].class, Integer[].class ),
        HEX_INT( SimpleParam.HEX_INT, int[].class, Integer[].class ),
        LONG( SimpleParam.LONG, long[].class, Long[].class ),
        HEX_LONG( SimpleParam.HEX_LONG, long[].class, Long[].class ),
        FLOAT( SimpleParam.FLOAT, float[].class, Float[].class ),
        DOUBLE( SimpleParam.DOUBLE, double[].class, Double[].class );
        private final SimpleParam component;
        private final Class<?>[] arrayTypes;

        private ListParam( SimpleParam component, Class<?>... arrayTypes )
        {
            this.component = component;
            for ( Class<?> type : this.arrayTypes = arrayTypes )
                if ( !type.isArray() ) throw new LinkageError( type + " is not an array type" );
        }

        @Override
        public boolean handles( Class<?>[] params )
        {
            if ( params.length != 1 ) return false;
            Class<?> param = params[0];
            if ( param == List.class ) return true;
            if ( !param.isArray() ) return false;
            for ( Class<?> type : arrayTypes )
            {
                if ( type == param ) return true;
            }
            return false;
        }

        @Override
        public String defaultInput()
        {
            return null;
        }

        @Override
        public Object[] performOn( String input, Method method )
        {
            Class<?> param = method.getParameterTypes()[0];
            Object result;
            if ( param.isArray() )
            {
                if ( param.getComponentType().isPrimitive() )
                {
                    result = component.primitiveArray( input );
                }
                else
                {
                    result = component.boxedArray( input );
                }
            }
            else
            {
                result = list( input );
            }
            return new Object[] { result };
        }

        @SuppressWarnings( "unchecked" )
        private List<?> list( String input )
        {
            if ( input == null || "".equals( input ) ) return Collections.emptyList();
            String[] parts = input.split( "," );
            @SuppressWarnings( "rawtypes" ) List result = new ArrayList( parts.length );
            for ( String part : parts )
            {
                result.add( component.convert( part ) );
            }
            return Collections.unmodifiableList( result );
        }

        void addAsSimple( Map<Class<?>, Conversion> simples )
        {
            for ( Class<?> type : arrayTypes )
                if ( simples.get( type ) == null ) simples.put( type, this );
        }

        void addTo( Map<Class<?>, ListParam> lists )
        {
            for ( Class<?> type : component.options )
                if ( !type.isPrimitive() ) lists.put( type, this );
        }
    }

    private enum MultiParam implements Conversion
    {
        HOST_AND_PORT( null, String.class, int.class )
        {
            @Override
            Object[] convert( String input )
            {
                int colon = ( input = input.trim() ).lastIndexOf( ':' );
                if ( colon < 0 )
                    throw new IllegalArgumentException( "No colon for separating the host and the port." );
                int port;
                try
                {
                    port = Integer.parseInt( input.substring( colon + 1 ) );
                }
                catch ( NumberFormatException cause )
                {
                    throw new IllegalArgumentException( cause );
                }
                return new Object[] { input.substring( 0, colon ), Integer.valueOf( port ) };
            }
        };
        private final String defaultInput;
        private final Class<?>[] params;

        private MultiParam( String defaultInput, Class<?>... params )
        {
            this.defaultInput = defaultInput;
            this.params = params;
        }

        @Override
        public boolean handles( Class<?>[] params )
        {
            return Arrays.equals( this.params, params );
        }

        @Override
        public String defaultInput()
        {
            return defaultInput;
        }

        @Override
        public Object[] performOn( String input, Method method )
        {
            return convert( input );
        }

        abstract Object[] convert( String input );
    }

    private final Conversion[] options;

    private SimpleParameterType( Conversion... options )
    {
        this.options = options;
    }

    Conversion conversionFor( Class<?>[] params )
    {
        for ( Conversion type : options )
            if ( type.handles( params ) ) return type;
        throw new IllegalArgumentException( "Cannot convert " + this + " to parameter of type "
                                            + Arrays.toString( params ) );
    }

    private static final Map<Class<?>, Conversion> SIMPLE;
    private static final Map<Class<?>, ListParam> LIST;
    static
    {
        Map<Class<?>, Conversion> simples = new HashMap<Class<?>, Conversion>();
        Map<Class<?>, ListParam> lists = new HashMap<Class<?>, ListParam>();
        for ( SimpleParam simple : SimpleParam.values() )
        {
            simple.addTo( simples );
        }
        for ( ListParam list : ListParam.values() )
        {
            list.addAsSimple( simples );
            list.addTo( lists );
        }
        SIMPLE = Collections.unmodifiableMap( simples );
        LIST = Collections.unmodifiableMap( lists );
    }

    static Conversion lookupConversion( Type[] params )
    {
        if ( params.length == 1 )
        {
            Type param = params[0];
            Conversion result = null;
            if ( param instanceof Class<?> )
            {
                result = SIMPLE.get( param );
            }
            else if ( param instanceof ParameterizedType && List.class == ( (ParameterizedType) param ).getRawType() )
            {
                param = ( (ParameterizedType) param ).getActualTypeArguments()[0];
                if ( param instanceof Class<?> )
                {
                    result = LIST.get( param );
                }
            }
            if ( result != null ) return result;
        }
        else if ( params.length > 1 )
        {
            Class<?>[] types = new Class<?>[params.length];
            for ( int i = 0; i < params.length; i++ )
            {
                if ( params[i] instanceof Class<?> )
                {
                    types[i] = (Class<?>) params[i];
                }
                else
                {
                    types = null;
                    break;
                }
            }
            if ( types != null ) for ( MultiParam multi : MultiParam.values() )
            {
                if ( multi.handles( types ) ) return multi;
            }
        }
        throw new IllegalArgumentException( "Cannot handle parameters of type: " + Arrays.toString( params ) );
    }
}
