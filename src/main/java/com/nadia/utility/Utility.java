package com.nadia.utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Главный класс утилиты.
 */
public class Utility {
    // Заголовки по-умолчанию для HTTP запросов.
    private static final Map<String, List<String>> DEFAULT_HEADER_FIELDS = new HashMap<String, List<String>>() { {
        put("Content-Type", Arrays.asList("application/json", "charset=utf-8"));
    } };
    // Поддерживаемые HTTP типы запросов для метода fetch(url)
    private static final Set<String> SUPPORTED_HTTP_METHODS = new HashSet<String>(
        Arrays.asList("GET", "POST", "PUT", "DELETE"));
    // размер буфера в байтах для загрузки файлов
    private static final int BUFFER_LENGTH_1024 = 1024;
    // HTTP код 400 для проверки ошибок
    private static final int RESPONSE_CODE_400 = 400;

    // Интерфейс для вызова кода при копировании файла
    public static interface Callback {
        void report(String data);
    }

    // Класс хранит ответ сервера (может быть бинарный или текстовый)
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

    // Класс для проверки запросов https
    static class NoHostnameVerifier implements javax.net.ssl.HostnameVerifier {
        public boolean verify(String hostname, javax.net.ssl.SSLSession session) {
              return true;
        }
    }

    // метод для считывания файла
    public static FetchResponse fetch(final String url) {
        return fetch(url, null, null, DEFAULT_HEADER_FIELDS, null, null);
    }

    // метод для считывания файла 2
    public static FetchResponse fetch(final String url, final String method, final String body,
        final Map<String, List<String>> headerFields, final Integer connectTimeout, final Integer readTimeout) {
        final String localMethod;
        if (SUPPORTED_HTTP_METHODS.contains(method)) {
            localMethod = method;
        } else {
            // Тип запроса по-умолчанию
            localMethod = "GET";
        }
        try {
            // Получить URL объект из строки
            final java.net.URL localUrl = new java.net.URL(url);
            // Открыть соединение
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
            // Прочитать содержимое файла из ответа
            final java.io.InputStream inputStream;
            if (responseCode < RESPONSE_CODE_400) {
                inputStream = connection.getInputStream();
            } else {
                inputStream = connection.getErrorStream();
            }
            // Буфер в памяти для сохранения содержимого файла
            final java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
            final byte[] buffer = new byte[BUFFER_LENGTH_1024];
            int length;
            // Скопировать в буфер
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

    // Метод для склеивания содержимого массива
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

    // Класс хранит статистику для обработанных файлов
    private static class Statistics {
        static AtomicInteger totalFilesCount = new AtomicInteger();
        static AtomicInteger filesCount = new AtomicInteger();
        static AtomicInteger filesSize = new AtomicInteger();
        static AtomicInteger downloadTime = new AtomicInteger();
        static AtomicInteger speed = new AtomicInteger();
    }

    // Класс хранит данные для файла links.txt
    private static class LinkItem {
        String url;
        String fileName;
    }

    // Класс хранит вызов для вычитывания файла из сети и сохранения его на диске
    private static class CallableImpl implements Callable<Void> {

        private final String outputFolder;
        private final LinkItem linkItem;
        private final Callback callback;

        public CallableImpl(String outputFolder, LinkItem linkItem, Callback callback) {
            this.outputFolder = outputFolder;
            this.linkItem = linkItem;
            this.callback = callback;
        }

        public Void call() {
            // Прочитать файл
            byte[] fileData = fetch(linkItem.url).blob();
            // Создать каталоги для файла
            new File(outputFolder).mkdir();
            // Сохранить текущее время в милисекундах
            long timeInSec = System.currentTimeMillis();
            try {
                // Сохранить файл на диске
                FileOutputStream stream = new FileOutputStream(outputFolder + "/"
                    + linkItem.fileName);
                stream.write(fileData);
                stream.close();
                int filesCount = Statistics.filesCount.incrementAndGet();
                int percent = 100 * filesCount / Statistics.totalFilesCount.get();
                int filesSize = Statistics.filesSize.addAndGet(fileData.length);
                int downloadTime = Statistics.downloadTime.addAndGet((int) (System.currentTimeMillis() - timeInSec));
                long speed = filesCount / (downloadTime == 0 ? 1 : downloadTime);
                callback.report(String.format("Завершено: %d%%\n"
                    + "Загружено: %d файлов, %d bytes\n"
                    + "Время: %d милисекунд\n"
                    + "Средняя скорость: %d файлов в милисекунду\n", percent, filesCount, filesSize, downloadTime, speed));

            } catch (IOException ex) {
            }
            return null;
        }
    }

    // Метод для вычитывания содержимого файла links.txt
    private static List<LinkItem> parseLinksFile(String linksFileName) {
        List<LinkItem> result = new ArrayList<LinkItem>();
        Path linksFilePath = Paths.get(linksFileName);
        try {
            List<String> lines = Files.readAllLines(linksFilePath, Charset.forName("UTF-8"));
            for (String line : lines) {
                if (line.matches("\\S+\\s+\\S+")) {
                    LinkItem linkItem = new LinkItem();
                    String[] linkItems = line.split("\\s+");
                    // Выделить url
                    linkItem.url = linkItems[0];
                    // Выделить имя файла
                    linkItem.fileName = linkItems[1];
                    result.add(linkItem);
                }
            }
        } catch (IOException ex) {
        }
        return result;
    }

    public static void downloadFiles(String threads, String outputFolder, String linksFileName, Callback callback) {
        List<Void> result = new ArrayList<Void>();
        // Создать Executor с числом потоков threads
        final ExecutorService executor = Executors.newFixedThreadPool(Integer.parseInt(threads));
        final List<Callable<Void>> callables = new ArrayList<Callable<Void>>();
        final List<LinkItem> linkItems = parseLinksFile(linksFileName);
        // Определить общее число файлов
        Statistics.totalFilesCount.set(linkItems.size());
        for (final LinkItem linkItem : linkItems) {
            callables.add(new CallableImpl(outputFolder, linkItem, callback));
        }
        try {
            // Запустить обработчики файлов в executor
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
                    System.out.println(data);
                }
            };
            // Вызов метода для загрузки файлов и сохранения их на диске
            downloadFiles(threads, outputFolder, linksFileName, callback);
        }
    }
}
