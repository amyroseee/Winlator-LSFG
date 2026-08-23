#include "descriptor_update_template.h"

DescriptorUpdateTemplateInfo* DescriptorUpdateTemplate_create(const VkDescriptorUpdateTemplateEntry* entries, uint32_t entryCount, VkPipelineBindPoint pipelineBindPoint) {
    DescriptorUpdateTemplateInfo* descriptorUpdateTemplateInfo = calloc(1, sizeof(DescriptorUpdateTemplateInfo));
    descriptorUpdateTemplateInfo->pipelineBindPoint = pipelineBindPoint;
    descriptorUpdateTemplateInfo->entries = malloc(entryCount * sizeof(VkDescriptorUpdateTemplateEntry));

    for (int i = 0; i < entryCount; i++) {
        VkDescriptorUpdateTemplateEntry* entry = &descriptorUpdateTemplateInfo->entries[i];
        memcpy(entry, &entries[i], sizeof(VkDescriptorUpdateTemplateEntry));

        if (IS_DESCRIPTOR_IMAGE_INFO(entry->descriptorType)) {
            descriptorUpdateTemplateInfo->imageInfoCount += entry->descriptorCount;
        }
        else if (IS_DESCRIPTOR_BUFFER_INFO(entry->descriptorType)) {
            descriptorUpdateTemplateInfo->bufferInfoCount += entry->descriptorCount;
        }
        else if (IS_DESCRIPTOR_TEXEL_BUFFER_VIEW(entry->descriptorType)) {
            descriptorUpdateTemplateInfo->texelBufferViewCount += entry->descriptorCount;
        }
    }

    descriptorUpdateTemplateInfo->entryCount = entryCount;
    return descriptorUpdateTemplateInfo;
}

void DescriptorUpdateTemplate_free(DescriptorUpdateTemplateInfo* descriptorUpdateTemplateInfo) {
    if (!descriptorUpdateTemplateInfo) return;
    descriptorUpdateTemplateInfo->entryCount = 0;
    MEMFREE(descriptorUpdateTemplateInfo->entries);
    MEMFREE(descriptorUpdateTemplateInfo);
}

void DescriptorUpdateTemplate_fillDescriptorWrites(DescriptorUpdateTemplateInfo* descriptorUpdateTemplateInfo, VkDescriptorSet descriptorSet, VkDescriptorImageInfo* imageInfos, VkDescriptorBufferInfo* bufferInfos, VkBufferView* texelBufferViews, VkWriteDescriptorSet* descriptorWrites, void* data) {
    int imageInfoOffset = 0;
    int bufferInfoOffset = 0;
    int texelBufferViewOffset = 0;

    for (int i = 0, j; i < descriptorUpdateTemplateInfo->entryCount; i++) {
        VkDescriptorUpdateTemplateEntry* entry = &descriptorUpdateTemplateInfo->entries[i];

        VkDescriptorImageInfo* pImageInfo = NULL;
        VkDescriptorBufferInfo* pBufferInfo = NULL;
        VkBufferView* pTexelBufferView = NULL;

        const char* ptr = data + entry->offset;
        
        if (IS_DESCRIPTOR_IMAGE_INFO(entry->descriptorType)) {
            pImageInfo = imageInfos + imageInfoOffset;
            for (j = 0; j < entry->descriptorCount; j++) {
                pImageInfo[j] = *(const VkDescriptorImageInfo*)ptr;
                ptr += entry->stride;
            }
            imageInfoOffset += entry->descriptorCount;
        }
        else if (IS_DESCRIPTOR_BUFFER_INFO(entry->descriptorType)) {
            pBufferInfo = bufferInfos + bufferInfoOffset;
            for (j = 0; j < entry->descriptorCount; j++) {
                pBufferInfo[j] = *(const VkDescriptorBufferInfo*)ptr;
                ptr += entry->stride;
            }
            bufferInfoOffset += entry->descriptorCount;
        }
        else if (IS_DESCRIPTOR_TEXEL_BUFFER_VIEW(entry->descriptorType)) {
            pTexelBufferView = texelBufferViews + texelBufferViewOffset;
            for (j = 0; j < entry->descriptorCount; j++) {
                pTexelBufferView[j] = *(const VkBufferView*)ptr;
                ptr += entry->stride;
            }
            texelBufferViewOffset += entry->descriptorCount;
        }

        descriptorWrites[i].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
        descriptorWrites[i].pNext = NULL,
        descriptorWrites[i].dstSet = descriptorSet;
        descriptorWrites[i].dstBinding = entry->dstBinding;
        descriptorWrites[i].dstArrayElement = entry->dstArrayElement;
        descriptorWrites[i].descriptorCount = entry->descriptorCount;
        descriptorWrites[i].descriptorType = entry->descriptorType;
        descriptorWrites[i].pImageInfo = pImageInfo;
        descriptorWrites[i].pBufferInfo = pBufferInfo;
        descriptorWrites[i].pTexelBufferView = pTexelBufferView;
    }
}