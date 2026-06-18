package com.centralalertas.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Converte objetos Java de/para JSON com o Gson. Uso UM unico objeto {@link Gson}
 * na aplicacao toda — ele e seguro para varias threads.
 */
public class Json {

    /*
     * Por que eu preciso de "adapters" para datas?
     * O Gson, por padrao, serializa lendo os campos do objeto por reflexao. Mas os tipos do
     * java.time (LocalDate/LocalDateTime) tem campos internos que o sistema de modulos do Java
     * nao deixa acessar -> da erro. Entao eu ensino o Gson a gravar como TEXTO no formato ISO
     * (ex.: "2026-06-20") e a ler de volta, registrando um adapter para cada tipo.
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .setPrettyPrinting() // saida identada, mais facil de ler ao testar
            .create();

    private Json() {
    }

    /** Objeto Java -> texto JSON. */
    public static String paraJson(Object o) {
        return GSON.toJson(o);
    }

    /** Texto JSON -> objeto Java da classe informada. */
    public static <T> T deJson(String texto, Class<T> tipo) {
        return GSON.fromJson(texto, tipo);
    }

    // Adapter de LocalDate: implementa serializar (-> JSON) e desserializar (<- JSON).
    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate data, Type tipo, JsonSerializationContext ctx) {
            return new JsonPrimitive(data.format(DateTimeFormatter.ISO_LOCAL_DATE)); // "yyyy-MM-dd"
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type tipo, JsonDeserializationContext ctx) {
            return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }

    // Adapter de LocalDateTime (ISO, ex.: "2026-06-20T14:30:00").
    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        @Override
        public JsonElement serialize(LocalDateTime data, Type tipo, JsonSerializationContext ctx) {
            return new JsonPrimitive(data.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type tipo, JsonDeserializationContext ctx) {
            return LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}
