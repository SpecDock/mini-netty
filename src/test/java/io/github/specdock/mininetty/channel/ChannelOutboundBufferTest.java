package io.github.specdock.mininetty.channel;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChannelOutboundBufferTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsRawByteBufferOutboundMessages() {
        ChannelOutboundBuffer outboundBuffer = new ChannelOutboundBuffer(null);
        DefaultChannelPromise promise = new DefaultChannelPromise();

        try {
            outboundBuffer.writeToBuffer(ByteBuffer.allocateDirect(4), promise);
        } finally {
            assertTrue(promise.isDone());
            assertFalse(promise.isSuccess());
        }
    }
}
