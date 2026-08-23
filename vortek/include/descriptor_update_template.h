#ifndef VORTEK_DESCRIPTOR_UPDATE_TEMPLATE_H
#define VORTEK_DESCRIPTOR_UPDATE_TEMPLATE_H

#include "vortek.h"

typedef struct DescriptorUpdateTemplateInfo {
    VkDescriptorUpdateTemplateEntry* entries;
    uint32_t entryCount;
    VkPipelineBindPoint pipelineBindPoint;
    uint32_t imageInfoCount;
    uint32_t bufferInfoCount;
    uint32_t texelBufferViewCount;
} DescriptorUpdateTemplateInfo;

extern DescriptorUpdateTemplateInfo* DescriptorUpdateTemplate_create(const VkDescriptorUpdateTemplateEntry* entries, uint32_t entryCount, VkPipelineBindPoint pipelineBindPoint);
extern void DescriptorUpdateTemplate_free(DescriptorUpdateTemplateInfo* descriptorUpdateTemplateInfo);
extern void DescriptorUpdateTemplate_fillDescriptorWrites(DescriptorUpdateTemplateInfo* descriptorUpdateTemplateInfo, VkDescriptorSet descriptorSet, VkDescriptorImageInfo* imageInfos, VkDescriptorBufferInfo* bufferInfos, VkBufferView* texelBufferViews, VkWriteDescriptorSet* descriptorWrites, void* data);

#endif
