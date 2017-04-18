package com.nadia.utility;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Utility {
    private static final Map<String, List<String>> DEFAULT_HEADER_FIELDS = new HashMap<String, List<String>>() { {
        put("Content-Type", Arrays.asList("application/json", "charset=utf-8"));
    } };
    private static final Set<String> SUPPORTED_HTTP_METHODS = new HashSet<String>(
        Arrays.asList("GET", "POST", "PUT", "DELETE"));
    private static final int BUFFER_LENGTH_1024 = 1024;
    private static final int RESPONSE_CODE_400 = 400;

    public static interface Callback {
        void report(String data);
    }

    public static class FetchResponse {
        private final boolean ok;
        private final int status;
        private final Map<String, List<String>> headerFields;
        private final java.io.ByteArrayOutputStream stream;

        public FetchResponse(final boolean ok, final int status, final Map<String, List<String>> headerFields,
            final java.io.ByteArrayOutputStream stream) {
            this.ok = ok;
            this.status = status;
            this.stream = stream;
            this.headerFields = headerFields;
        }

        public boolean isOk() {
            return ok;
        }

        public int getStatus() {
            return status;
        }

        public Map<String, List<String>> getHeaderFields() {
            return headerFields;
        }

        public byte[] blob() {
            return stream.toByteArray();
        }

        public String text() {
            try {
                return stream.toString("UTF-8");
            } catch (java.io.UnsupportedEncodingException ex) {
                throw new UnsupportedOperationException(ex);
            }
        }
    }

    static class NoHostnameVerifier implements javax.net.ssl.HostnameVerifier {
        public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
              return true;
        }
    }

    public static FetchResponse fetch(final String url) {
        return fetch(url, null, null, DEFAULT_HEADER_FIELDS, null, null);
    }

    public static FetchResponse fetch(final String url, final String method, final String body,
        final Map<String, List<String>> headerFields, final Integer connectTimeout, final Integer readTimeout) {
        final String localMethod;
        if (SUPPORTED_HTTP_METHODS.contains(method)) {
            localMethod = method;
        } else {
            localMethod = "GET";
        }
        try {
            final java.net.URL localUrl = new java.net.URL(url);
            final java.net.HttpURLConnection connection = (java.net.HttpURLConnection) localUrl.openConnection();
            connection.setRequestMethod(localMethod);
            if (connectTimeout != null) {
                connection.setConnectTimeout(connectTimeout);
            }
            if (readTimeout != null) {
                connection.setReadTimeout(readTimeout);
            }
            if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                ((javax.net.ssl.HttpsURLConnection) connection).setHostnameVerifier(new NoHostnameVerifier());
            }
            if (headerFields != null) {
                for (final Map.Entry<String, List<String>> header : headerFields.entrySet()) {
                    connection.setRequestProperty(header.getKey(), join(header.getValue(), ";"));
                }
            }
            if (body != null) {
                connection.setDoOutput(true);
                final java.io.DataOutputStream outputStream =
                    new java.io.DataOutputStream(connection.getOutputStream());
                outputStream.writeBytes(body);
                outputStream.flush();
                outputStream.close();
            }
            final int responseCode = connection.getResponseCode();
            final java.io.InputStream inputStream;
            if (responseCode < RESPONSE_CODE_400) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            final java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_LENGTH_1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            inputStream.close();
            return new FetchResponse(responseCode < RESPONSE_CODE_400, responseCode, connection.getHeaderFields(),
                result);
        } catch (java.io.IOException ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    static <T> String join(final Iterable<T> iterable, final String separator) {
        final StringBuilder sb = new StringBuilder();
        int index = 0;
        for (final T item : iterable) {
            if (index > 0) {
                sb.append(separator);
            }
            sb.append(item.toString());
            index += 1;
        }
        return sb.toString();
    }

    private static class LinkItem {
        String url;
        String fileName;
    }

    private static class CallableImpl implements Callable<Void> {

        private final String outputFolder;
        private final List<LinkItem> linkItems;
        private final Callback callback;

        public CallableImpl(String outputFolder, List<LinkItem> linkItems, Callback callback) {
            this.outputFolder = outputFolder;
            this.linkItems = linkItems;
            this.callback = callback;
        }

        public Void call() {
            return null;
        }
    }

    public static void downloadFiles(String threads, String outputFolder, String linksFileName, Callback callback) {
        List<Void> result = new ArrayList<Void>();
        final ExecutorService executor = Executors.newFixedThreadPool(100);
        final List<Callable<Void>> callables = new ArrayList<Callable<Void>>();
        try {
            for (Future<Void> future : executor.invokeAll(callables)) {
                try {
                    result.add(future.get());
                } catch (ExecutionException ex) {
                    System.out.println("ExecutionException - " + ex.getMessage());
                }
            }
        } catch (InterruptedException ex) {
            System.out.println("InterruptedException - " + ex.getMessage());
        }
        executor.shutdown();
    }

    public static void main(String[] args) {
        String threads = "5";
        String outputFolder = "output_folder";
        String linksFileName = "links.txt";
        if (args.length == 0) {
            System.out.println("Usage: java -jar utility.jar 5 output_folder links.txt");
        } else {
            if (args.length >= 1) {
                threads = args[0];
            } else if (args.length >= 2) {
                outputFolder = args[1];
            } else if (args.length >= 3) {
                linksFileName = args[2];
            }
            Callback callback = new Callback() {
                public void report(String data) {
                }
            };
            downloadFiles(threads, outputFolder, linksFileName, callback);
        }
    }
}
