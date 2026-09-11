package bsh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredTestRunner.class)
public class ParserInputTest {

    private static final class TrackingReader extends StringReader {
        private int readsAfterEnd;
        private boolean ended;
        private boolean closed;

        TrackingReader(String s) {
            super(s);
        }

        @Override
        public int read(char[] buf, int off, int len) throws IOException {
            if (ended)
                readsAfterEnd++;
            int n = super.read(buf, off, len);
            if (n == -1)
                ended = true;
            return n;
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    @Test
    public void eval_stops_reading_input_at_end_of_stream() throws Exception {
        Interpreter interpreter = new Interpreter();
        TrackingReader in = new TrackingReader("x = 1;\ny = x + 1;\n");
        interpreter.eval(in, interpreter.getNameSpace(), "test");
        assertEquals(Integer.valueOf(2), interpreter.get("y"));
        assertTrue("input closed at end of stream", in.closed);
        assertEquals("reads after end of stream", 0, in.readsAfterEnd);
    }

    @Test
    public void end_of_input_is_signalled_without_a_stack_trace() throws Exception {
        TrackingReader source = new TrackingReader("a");
        Reader in = StacklessEofReader.wrap(source);
        char[] buf = new char[4];
        assertEquals(1, in.read(buf, 0, buf.length));
        try {
            in.read(buf, 0, buf.length);
            fail("Expected end of input");
        } catch (IOException end) {
            assertEquals(0, end.getStackTrace().length);
            assertTrue("source closed at end of input", source.closed);
            try {
                in.read(buf, 0, buf.length);
                fail("Expected end of input");
            } catch (IOException again) {
                assertSame(end, again);
            }
        }
        assertEquals("reads after end of stream", 0, source.readsAfterEnd);
        assertSame(in, StacklessEofReader.wrap(in));
    }
}
