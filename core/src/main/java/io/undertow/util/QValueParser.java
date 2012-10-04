/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for parsing headers that accept q values
 *
 * @author Stuart Douglas
 */
public class QValueParser {

    private QValueParser() {

    }

    /**
     * Parses a set of headers that take q values to determine the most preferred one.
     *
     * It returns the result in the form of a sorted list of sets, with every element in
     * the set having the same q value. This means the highest priority items are at the
     * front of the list. The container should use its own internal preferred ordering
     * to determinately pick the correct item to use
     *
     * @param headers The headers
     * @return The q value results
     */
    public static List<Set<QValueResult>> parse(Deque<String> headers) {
        final List<QValueResult> found = new ArrayList<QValueResult>();
        QValueResult current = null;
        for (final String header : headers) {
            final int l = header.length();
            //we do not use a string builder
            //we just keep track of where the current string starts and call substring()
            int stringStart = 0;
            for (int i = 0; i < l; ++i) {
                char c = header.charAt(i);
                switch (c) {
                    case ',': {
                        if (current != null &&
                                (i - stringStart > 2 && header.charAt(stringStart) == 'q' &&
                                        header.charAt(stringStart + 1) == '=')) {
                            //if this is a valid qvalue
                            current.qvalue = header.substring(stringStart + 2, i);
                            current = null;
                        } else if (stringStart != i) {
                            current = handleNewEncoding(found, header, stringStart, i);
                        }
                        stringStart = i + 1;
                        break;
                    }
                    case ';': {
                        if (stringStart != i) {
                            current = handleNewEncoding(found, header, stringStart, i);
                            stringStart = i + 1;
                        }
                        break;
                    }
                    case ' ': {
                        if (stringStart != i) {
                            if (current != null &&
                                    (i - stringStart > 2 && header.charAt(stringStart) == 'q' &&
                                            header.charAt(stringStart + 1) == '=')) {
                                //if this is a valid qvalue
                                current.qvalue = header.substring(stringStart + 2, i);
                            } else {
                                current = handleNewEncoding(found, header, stringStart, i);
                            }
                        }
                        stringStart = i + 1;
                    }
                }
            }

            if (stringStart != l) {
                if (current != null &&
                        (l - stringStart > 2 && header.charAt(stringStart) == 'q' &&
                                header.charAt(stringStart + 1) == '=')) {
                    //if this is a valid qvalue
                    current.qvalue = header.substring(stringStart + 2, l);
                } else {
                    current = handleNewEncoding(found, header, stringStart, l);
                }
            }
        }
        Collections.sort(found, Collections.reverseOrder());
        String currentQValue = null;
        List<Set<QValueResult>> values = new ArrayList<Set<QValueResult>>();
        Set<QValueResult> currentSet = null;

        for(QValueResult val : found) {
            if(!val.qvalue.equals(currentQValue)) {
                currentQValue = val.qvalue;
                currentSet = new HashSet<QValueResult>();
                values.add(currentSet);
            }
            currentSet.add(val);
        }
        return values;
    }

    private static QValueResult handleNewEncoding(final List<QValueResult> found, final String header, final int stringStart, final int i) {
        final QValueResult current = new QValueResult();
        current.value = header.substring(stringStart, i);
        found.add(current);
        return current;
    }

    public static class QValueResult implements Comparable<QValueResult> {


        /**
         * The string value of the result
         */
        private String value;

        /**
         * we keep the qvalue as a string to avoid parsing the double.
         * <p/>
         * This should give both performance and also possible security improvements
         */
        private String qvalue = "1";

        public String getValue() {
            return value;
        }

        public String getQvalue() {
            return qvalue;
        }

        @Override
        public int compareTo(final QValueResult other) {
            //we compare the strings as if they were decimal values.
            //we know they can only be

            final String t = qvalue;
            final String o = other.qvalue;
            if (t == null && o == null) {
                //neither of them has a q value
                //we compare them via the server specified default precedence
                //note that encoding is never null here, a * without a q value is meaningless
                //and will be discarded before this
                return 0;
            }

            if (o == null) {
                return 1;
            } else if (t == null) {
                return -1;
            }

            final int tl = t.length();
            final int ol = o.length();
            //we only compare the first 5 characters as per spec
            for (int i = 0; i < 5; ++i) {
                if (tl == i || ol == i) {
                    return ol - tl; //longer one is higher
                }
                if (i == 1) continue; // this is just the decimal point
                final int tc = t.charAt(i);
                final int oc = o.charAt(i);

                int res = tc - oc;
                if (res != 0) {
                    return res;
                }
            }
            return 0;
        }
    }
}
