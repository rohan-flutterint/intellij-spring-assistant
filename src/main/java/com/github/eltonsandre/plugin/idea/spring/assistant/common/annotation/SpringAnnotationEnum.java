package com.github.eltonsandre.plugin.idea.spring.assistant.common.annotation;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum SpringAnnotationEnum {

    VALUE("Value", "org.springframework.beans.factory.annotation.Value", true),
    CONFIGURATION_PROPERTIES("ConfigurationProperties", "org.springframework.boot.context.properties.ConfigurationProperties", false),
    CONDITIONAL_ON_EXPRESSION("ConditionalOnExpression", "org.springframework.boot.autoconfigure.condition.ConditionalOnExpression", true),

    KAFKA_LISTENER("KafkaListener", "org.springframework.kafka.annotation.KafkaListener", true),
    ROCKETMQ_MESSAGE_LISTENER("RocketMQMessageListener", "org.apache.rocketmq.spring.annotation.RocketMQMessageListener", true),

    FEIGN_CLIENT("FeignClient", "org.springframework.cloud.openfeign.FeignClient", true),

    GET_MAPPING("GetMapping", "org.springframework.web.bind.annotation.GetMapping", true),
    PUT_MAPPING("PutMapping", "org.springframework.web.bind.annotation.PutMapping", true),
    POST_MAPPING("PostMapping", "org.springframework.web.bind.annotation.PostMapping", true),
    PATCH_MAPPING("PatchMapping", "org.springframework.web.bind.annotation.PatchMapping", true),
    DELETE_MAPPING("DeleteMapping", "org.springframework.web.bind.annotation.DeleteMapping", true),
    REQUEST_MAPPING("RequestMapping", "org.springframework.web.bind.annotation.RequestMapping", true),

    UNKNOW("AnnotationUnknow", "annotation.unknow", false);

    private final String name;
    private final String qualifiedName;
    private final boolean hasPlaceholder;

    private static final Map<String, SpringAnnotationEnum> VALUES = Arrays.stream(SpringAnnotationEnum.values())
            .collect(Collectors.toMap(SpringAnnotationEnum::getQualifiedName, e -> e));

    public static SpringAnnotationEnum fromQualifiedName(@NonNull final String qualifiedName) {
        return VALUES.getOrDefault(qualifiedName, UNKNOW);
    }

    public static boolean contains(final String qualifiedName) {
        return VALUES.containsKey(qualifiedName);
    }

    public static boolean notContains(final String qualifiedName) {
        return !contains(qualifiedName);
    }

}