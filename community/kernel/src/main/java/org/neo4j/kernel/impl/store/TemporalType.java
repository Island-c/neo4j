/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.store;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.helpers.collection.Pair;
import org.neo4j.kernel.impl.store.format.standard.StandardFormatSettings;
import org.neo4j.kernel.impl.store.record.PropertyBlock;
import org.neo4j.values.storable.ArrayValue;
import org.neo4j.values.storable.DateTimeValue;
import org.neo4j.values.storable.DateValue;
import org.neo4j.values.storable.DurationValue;
import org.neo4j.values.storable.LocalDateTimeValue;
import org.neo4j.values.storable.LocalTimeValue;
import org.neo4j.values.storable.LongArray;
import org.neo4j.values.storable.TimeValue;
import org.neo4j.values.storable.Value;
import org.neo4j.values.storable.Values;

import static java.time.ZoneOffset.UTC;

/**
 * For the PropertyStore format, check {@link PropertyStore}.
 * For the array format, check {@link DynamicArrayStore}.
 */
public enum TemporalType
{
    TEMPORAL_INVALID( 0, "Invalid" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    throw new UnsupportedOperationException( "Cannot decode invalid temporal" );
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    return PropertyType.BLOCKS_USED_FOR_BAD_TYPE_OR_ENCODING;
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    throw new UnsupportedOperationException( "Cannot decode invalid temporal array" );
                }
            },
    TEMPORAL_DATE( 1, "Date" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    long epochDay = valueIsInlined( valueBlocks[0] ) ? valueBlocks[offset] >>> 33 : valueBlocks[1 + offset];
                    return DateValue.epochDate( epochDay );
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    return valueIsInlined( firstBlock ) ? 1 : 2;
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    if ( dataValue instanceof LongArray )
                    {
                        LongArray numbers = (LongArray) dataValue;
                        LocalDate[] dates = new LocalDate[numbers.length()];
                        for ( int i = 0; i < dates.length; i++ )
                        {
                            dates[i] = LocalDate.ofEpochDay( numbers.longValue( i ) );
                        }
                        return Values.dateArray( dates );
                    }
                    else
                    {
                        throw new RuntimeException( "Array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                    }
                }

                private boolean valueIsInlined( long firstBlock )
                {
                    // [][][][i][ssss,tttt][kkkk,kkkk][kkkk,kkkk][kkkk,kkkk]
                    return (firstBlock & 0x100000000L) > 0;
                }
            },
    TEMPORAL_LOCAL_TIME( 2, "LocalTime" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    long nanoOfDay = valueIsInlined( valueBlocks[0] ) ? valueBlocks[offset] >>> 33 : valueBlocks[1 + offset];
                    return LocalTimeValue.localTime( nanoOfDay );
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    return valueIsInlined( firstBlock ) ? 1 : 2;
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    if ( dataValue instanceof LongArray )
                    {
                        LongArray numbers = (LongArray) dataValue;
                        LocalTime[] times = new LocalTime[numbers.length()];
                        for ( int i = 0; i < times.length; i++ )
                        {
                            times[i] = LocalTime.ofNanoOfDay( numbers.longValue( i ) );
                        }
                        return Values.localTimeArray( times );
                    }
                    else
                    {
                        throw new RuntimeException( "Array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                    }
                }

                private boolean valueIsInlined( long firstBlock )
                {
                    // [][][][i][ssss,tttt][kkkk,kkkk][kkkk,kkkk][kkkk,kkkk]
                    return (firstBlock & 0x100000000L) > 0;
                }
            },
    TEMPORAL_LOCAL_DATE_TIME( 3, "LocalDateTime" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    long nanoOfSecond = valueBlocks[offset] >>> 32;
                    long epochSecond = valueBlocks[1 + offset];
                    return LocalDateTimeValue.localDateTime( epochSecond, nanoOfSecond );
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    return 2;
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    if ( dataValue instanceof LongArray )
                    {
                        LongArray numbers = (LongArray) dataValue;
                        LocalDateTime[] dateTimes = new LocalDateTime[numbers.length() / 2];
                        for ( int i = 0; i < dateTimes.length; i++ )
                        {
                            dateTimes[i] = LocalDateTime.ofInstant( Instant.ofEpochSecond( numbers.longValue( i * 2 ), numbers.longValue( i * 2 + 1 ) ), UTC );
                        }
                        return Values.localDateTimeArray( dateTimes );
                    }
                    else
                    {
                        throw new RuntimeException( "Array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                    }
                }
            },
    TEMPORAL_TIME( 4, "Time" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    int minuteOffset = (int) (valueBlocks[offset] >>> 32);
                    long nanoOfDay = valueBlocks[1 + offset];
                    return TimeValue.time( nanoOfDay, ZoneOffset.ofTotalSeconds( minuteOffset * 60 ) );
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    return 2;
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    if ( dataValue instanceof LongArray )
                    {
                        LongArray numbers = (LongArray) dataValue;
                        OffsetTime[] times = new OffsetTime[(int) (numbers.length() / 1.25)];
                        for ( int i = 0; i < times.length; i++ )
                        {
                            long nanoOfDay = numbers.longValue( i );
                            int shift = (i % 4) * 16;
                            short minuteOffset = (short) (numbers.longValue( times.length + i / 4 ) >>> shift);
                            times[i] = OffsetTime.of( LocalTime.ofNanoOfDay( nanoOfDay ), ZoneOffset.ofTotalSeconds( minuteOffset * 60 ) );
                        }
                        return Values.timeArray( times );
                    }
                    else
                    {
                        throw new RuntimeException( "Array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                    }
                }
            },
    TEMPORAL_DATE_TIME( 5, "DateTime" )
            {
                @Override
                public Value decodeForTemporal( long[] valueBlocks, int offset )
                {
                    if ( storingZoneOffset( valueBlocks[0] ) )
                    {
                        int nanoOfSecond = (int) (valueBlocks[offset] >>> 33);
                        long epochSecond = valueBlocks[1 + offset];
                        int minuteOffset = (int) valueBlocks[2 + offset];
                        return DateTimeValue.datetime( epochSecond, nanoOfSecond, ZoneOffset.ofTotalSeconds( minuteOffset * 60 ) );
                    }
                    else
                    {
                        throw new UnsupportedOperationException( "Cannot yet store DateTime with ZoneID" );
                    }
                }

                @Override
                public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
                {
                    if ( storingZoneOffset( firstBlock ) )
                    {
                        return 3;
                    }
                    else
                    {
                        // TODO proper number
                        return 3;
                    }
                }

                @Override
                public ArrayValue decodeArray( Value dataValue )
                {
                    if ( dataValue instanceof LongArray )
                    {
                        LongArray numbers = (LongArray) dataValue;
                        ZonedDateTime[] dateTimes = new ZonedDateTime[numbers.length() / 3];
                        for ( int i = 0; i < dateTimes.length; i++ )
                        {
                            long epochSecond = numbers.longValue( i * 3 );
                            long nanos = numbers.longValue( i * 3 + 1 );
                            long zoneValue = numbers.longValue( i * 3 + 2 );
                            if ( (zoneValue & 1) == 1 )
                            {
                                short minuteOffset = (short) (zoneValue >>> 1);
                                dateTimes[i] =
                                        ZonedDateTime.ofInstant( Instant.ofEpochSecond( epochSecond, nanos ), ZoneOffset.ofTotalSeconds( minuteOffset * 60 ) );
                            }
                            else
                            {
                                throw new UnsupportedOperationException( "Cannot yet store DateTime with ZoneID" );
                            }
                        }
                        return Values.dateTimeArray( dateTimes );
                    }
                    else
                    {
                        throw new RuntimeException(
                                "LocalTime array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                    }
                }

                private boolean storingZoneOffset( long firstBlock )
                {
                    // [][][][i][ssss,tttt][kkkk,kkkk][kkkk,kkkk][kkkk,kkkk]
                    return (firstBlock & 0x100000000L) > 0;
                }
            },
    TEMPORAL_DURATION( 6, "Duration" )
        {
            @Override
            public Value decodeForTemporal( long[] valueBlocks, int offset )
            {
                int nanos = (int) (valueBlocks[offset] >>> 32);
                long months = valueBlocks[1 + offset];
                long days = valueBlocks[2 + offset];
                long seconds = valueBlocks[3 + offset];
                return DurationValue.duration( months, days, seconds, nanos );
            }

            @Override
            public int calculateNumberOfBlocksUsedForTemporal( long firstBlock )
            {
                return 4;
            }

            @Override
            public ArrayValue decodeArray( Value dataValue )
            {
                if ( dataValue instanceof LongArray )
                {
                    LongArray numbers = (LongArray) dataValue;
                    DurationValue[] durations = new DurationValue[(int) (numbers.length() / 3)];
                    for ( int i = 0; i < durations.length; i++ )
                    {
                        durations[i] = DurationValue.duration( numbers.longValue( i * 4 ), numbers.longValue( i * 4 + 1 ), numbers.longValue( i * 4 + 2 ),
                                numbers.longValue( i * 4 + 3 ) );
                    }
                    return Values.durationArray( durations );
                }
                else
                {
                    throw new RuntimeException( "Array with unexpected type. Actual:" + dataValue.getClass().getSimpleName() + ". Expected: LongArray." );
                }
            }
        };

    /**
     * Handler for header information for Temporal objects and arrays of Temporal objects
     */
    public static class TemporalHeader
    {
        private final int temporalType;

        private TemporalHeader( int temporalType )
        {
            this.temporalType = temporalType;
        }

        private void writeArrayHeaderTo( byte[] bytes )
        {
            bytes[0] = (byte) PropertyType.TEMPORAL.intValue();
            bytes[1] = (byte) temporalType;
        }

        static TemporalHeader fromArrayHeaderBytes( byte[] header )
        {
            int temporalType = Byte.toUnsignedInt( header[1] );
            return new TemporalHeader( temporalType );
        }

        public static TemporalHeader fromArrayHeaderByteBuffer( ByteBuffer buffer )
        {
            int temporalType = Byte.toUnsignedInt( buffer.get() );
            return new TemporalHeader( temporalType );
        }
    }

    private static final TemporalType[] TYPES = TemporalType.values();
    private static final Map<String,TemporalType> all = new HashMap<>( TYPES.length );

    static
    {
        for ( TemporalType temporalType : TYPES )
        {
            all.put( temporalType.name, temporalType );
        }
    }

    private static final long TEMPORAL_TYPE_MASK = 0x00000000F0000000L;

    private static int getTemporalType( long firstBlock )
    {
        return (int) ((firstBlock & TEMPORAL_TYPE_MASK) >> 28);
    }

    public static int calculateNumberOfBlocksUsed( long firstBlock )
    {
        TemporalType geometryType = find( getTemporalType( firstBlock ) );
        return geometryType.calculateNumberOfBlocksUsedForTemporal( firstBlock );
    }

    private static TemporalType find( int temporalType )
    {
        if ( temporalType < TYPES.length && temporalType >= 0 )
        {
            return TYPES[temporalType];
        }
        else
        {
            // Kernel code requires no exceptions in deeper PropertyChain processing of corrupt/invalid data
            return TEMPORAL_INVALID;
        }
    }

    public static TemporalType find( String name )
    {
        TemporalType table = all.get( name );
        if ( table != null )
        {
            return table;
        }
        else
        {
            throw new IllegalArgumentException( "No known Temporal Type: " + name );
        }
    }

    public static Value decode( PropertyBlock block )
    {
        return decode( block.getValueBlocks(), 0 );
    }

    public static Value decode( long[] valueBlocks, int offset )
    {
        long firstBlock = valueBlocks[offset];
        int temporalType = getTemporalType( firstBlock );
        return find( temporalType ).decodeForTemporal( valueBlocks, offset );
    }

    public static long[] encodeDate( int keyId, long epochDay )
    {
        return encodeLong( keyId, epochDay, TemporalType.TEMPORAL_DATE.temporalType );
    }

    public static long[] encodeLocalTime( int keyId, long nanoOfDay )
    {
        return encodeLong( keyId, nanoOfDay, TemporalType.TEMPORAL_LOCAL_TIME.temporalType );
    }

    private static long[] encodeLong( int keyId, long val, int temporalType )
    {
        int idBits = StandardFormatSettings.PROPERTY_TOKEN_MAXIMUM_ID_BITS;

        long keyAndType = keyId | (((long) (PropertyType.TEMPORAL.intValue()) << idBits));
        long temporalTypeBits = temporalType << (idBits + 4);

        long[] data;
        if ( ShortArray.LONG.getRequiredBits( val ) <= 64 - 33 )
        {   // We only need one block for this value
            data = new long[1];
            data[0] = keyAndType | temporalTypeBits | (1L << 32) | (val << 33);
        }
        else
        {   // We need two blocks for this value
            data = new long[2];
            data[0] = keyAndType | temporalTypeBits;
            data[1] = val;
        }

        return data;
    }

    public static long[] encodeLocalDateTime( int keyId, long epochSecond, long nanoOfSecond )
    {
        int idBits = StandardFormatSettings.PROPERTY_TOKEN_MAXIMUM_ID_BITS;

        long keyAndType = keyId | (((long) (PropertyType.TEMPORAL.intValue()) << idBits));
        long temporalTypeBits = TemporalType.TEMPORAL_LOCAL_DATE_TIME.temporalType << (idBits + 4);

        long[] data = new long[2];
        // nanoOfSecond will never require more than 30 bits
        data[0] = keyAndType | temporalTypeBits | (nanoOfSecond << 32);
        data[1] = epochSecond;

        return data;
    }

    public static long[] encodeDateTime( int keyId, long epochSecond, long nanoOfSecond, String zoneId )
    {
        throw new UnsupportedOperationException( "Cannot yet store DateTime with ZoneID" );
    }

    public static long[] encodeDateTime( int keyId, long epochSecond, long nanoOfSecond, int secondOffset )
    {
        int idBits = StandardFormatSettings.PROPERTY_TOKEN_MAXIMUM_ID_BITS;
        int minuteOffset = secondOffset / 60;

        long keyAndType = keyId | (((long) (PropertyType.TEMPORAL.intValue()) << idBits));
        long temporalTypeBits = TemporalType.TEMPORAL_DATE_TIME.temporalType << (idBits + 4);

        long[] data = new long[3];
        // nanoOfSecond will never require more than 30 bits
        data[0] = keyAndType | temporalTypeBits | (1L << 32) | (nanoOfSecond << 33);
        data[1] = epochSecond;
        data[2] = minuteOffset;

        return data;
    }

    public static long[] encodeTime( int keyId, long nanoOfDay, int secondOffset )
    {
        int idBits = StandardFormatSettings.PROPERTY_TOKEN_MAXIMUM_ID_BITS;
        int minuteOffset = secondOffset / 60;

        long keyAndType = keyId | (((long) (PropertyType.TEMPORAL.intValue()) << idBits));
        long temporalTypeBits = TemporalType.TEMPORAL_TIME.temporalType << (idBits + 4);

        long[] data = new long[2];
        // Offset are always in the range +-18:00, so minuteOffset will never require more than 12 bits
        data[0] = keyAndType | temporalTypeBits | ((long) minuteOffset << 32);
        data[1] = nanoOfDay;

        return data;
    }

    public static long[] encodeDuration( int keyId, long months, long days, long seconds, int nanos )
    {
        int idBits = StandardFormatSettings.PROPERTY_TOKEN_MAXIMUM_ID_BITS;

        long keyAndType = keyId | (((long) (PropertyType.TEMPORAL.intValue()) << idBits));
        long temporalTypeBits = TemporalType.TEMPORAL_DURATION.temporalType << (idBits + 4);

        long[] data = new long[4];
        data[0] = keyAndType | temporalTypeBits | ((long) nanos << 32);
        data[1] = months;
        data[2] = days;
        data[3] = seconds;

        return data;
    }

    public static byte[] encodeDateArray( LocalDate[] dates )
    {
        long[] data = new long[dates.length];
        for ( int i = 0; i < data.length; i++ )
        {
            data[i] = dates[i].toEpochDay();
        }
        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_DATE.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static byte[] encodeLocalTimeArray( LocalTime[] times )
    {
        long[] data = new long[times.length];
        for ( int i = 0; i < data.length; i++ )
        {
            data[i] = times[i].toNanoOfDay();
        }
        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_LOCAL_TIME.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static byte[] encodeLocalDateTimeArray( LocalDateTime[] dateTimes )
    {
        long[] data = new long[dateTimes.length * 2];
        for ( int i = 0; i < dateTimes.length; i++ )
        {
            data[i * 2] = dateTimes[i].toEpochSecond( UTC );
            data[i * 2 + 1] = dateTimes[i].getNano();
        }
        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_LOCAL_DATE_TIME.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static byte[] encodeTimeArray( OffsetTime[] times )
    {
        // TODO this sucks in terms of cache locality
        long[] data = new long[(int) (Math.ceil( times.length * 1.25 ))];
        // First all nano of days (each is a long)
        int i;
        for ( i = 0; i < times.length; i++ )
        {
            data[i] = times[i].toLocalTime().toNanoOfDay();
        }
        // Then all minuteOffsets (each fits in a short)
        for ( int j = 0; j < times.length; j++ )
        {
            int shift = (j % 4) * 16;
            short minuteOffset = (short) (times[j].getOffset().getTotalSeconds() / 60);
            data[i] = (Short.toUnsignedLong( minuteOffset )) << shift | data[i];
            if ( j % 4 == 3 )
            {
                i++;
            }
        }

        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_TIME.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static byte[] encodeDateTimeArray( ZonedDateTime[] dateTimes )
    {
        // TODO we can store this in dateTimes.length * 2.25
        long[] data = new long[dateTimes.length * 3];

        int i;
        for ( i = 0; i < dateTimes.length; i++ )
        {
            data[i * 3] = dateTimes[i].toEpochSecond();
            data[i * 3 + 1] = dateTimes[i].getNano();
            if ( dateTimes[i].getZone() instanceof ZoneOffset )
            {
                ZoneOffset offset = (ZoneOffset) dateTimes[i].getZone();
                int minuteOffset = offset.getTotalSeconds() / 60;
                // Set lowest bit to 1 means offset
                data[i * 3 + 2] = minuteOffset << 1 | 1L;
            }
            else
            {
                // Set lowest bit to 0 means zone id
                throw new UnsupportedOperationException( "Cannot yet store DateTime with ZoneID" );
            }
        }

        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_DATE_TIME.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static byte[] encodeDurationArray( DurationValue[] durations )
    {
        long[] data = new long[durations.length * 4];
        for ( int i = 0; i < durations.length; i++ )
        {
            data[i * 4] = durations[i].get( ChronoUnit.MONTHS );
            data[i * 4 + 1] = durations[i].get( ChronoUnit.DAYS );
            data[i * 4 + 2] = durations[i].get( ChronoUnit.SECONDS );
            data[i * 4 + 3] = durations[i].get( ChronoUnit.NANOS );
        }
        TemporalHeader header = new TemporalHeader( TemporalType.TEMPORAL_DURATION.temporalType );
        byte[] bytes = DynamicArrayStore.encodeFromNumbers( data, DynamicArrayStore.TEMPORAL_HEADER_SIZE );
        header.writeArrayHeaderTo( bytes );
        return bytes;
    }

    public static ArrayValue decodeTemporalArray( TemporalHeader header, byte[] data )
    {
        byte[] dataHeader = PropertyType.ARRAY.readDynamicRecordHeader( data );
        byte[] dataBody = new byte[data.length - dataHeader.length];
        System.arraycopy( data, dataHeader.length, dataBody, 0, dataBody.length );
        Value dataValue = DynamicArrayStore.getRightArray( Pair.of( dataHeader, dataBody ) );
        return find( header.temporalType ).decodeArray( dataValue );
    }

    private final int temporalType;
    private final String name;

    TemporalType( int temporalType, String name )
    {
        this.temporalType = temporalType;
        this.name = name;
    }

    public abstract Value decodeForTemporal( long[] valueBlocks, int offset );

    public abstract int calculateNumberOfBlocksUsedForTemporal( long firstBlock );

    public abstract ArrayValue decodeArray( Value dataValue );

    // TODO use that ting man
    public int getTemporalType()
    {
        return temporalType;
    }

    public String getName()
    {
        return name;
    }
}
