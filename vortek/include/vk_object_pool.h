#ifndef VK_OBJECT_POOL_H
#define VK_OBJECT_POOL_H

#include "vortek.h"

typedef struct VkObjectPool {
    VkObjectType type;
    bool canFreeObjects;
    int position;
    ArrayList availableObjects;
    ArrayList usedObjects;
} VkObjectPool;

extern VkObjectPool* VkObjectPool_create(VkObjectType type, int size, bool canFreeObjects);
extern VkObject* VkObjectPool_get(VkObjectPool* objectPool);
extern void VkObjectPool_reset(VkObjectPool* objectPool);
extern void VkObjectPool_destroy(VkObjectPool* objectPool);
extern void VkObjectPool_freeObject(VkObjectPool* objectPool, VkObject* usedObject);

#endif