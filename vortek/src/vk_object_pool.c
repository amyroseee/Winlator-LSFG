#include "vk_object_pool.h"

static void freeArrayListElements(ArrayList* arrayList) {
    for (int i = 0; i < arrayList->size; i++) {
        VkObject* object = arrayList->elements[i];
        if (!VKOBJECT_IS_NULL(object)) free(object);
    }
    free(arrayList->elements);
}

VkObjectPool* VkObjectPool_create(VkObjectType type, int size, bool canFreeObjects) {
    VkObjectPool* objectPool = calloc(1, sizeof(VkObjectPool));
    objectPool->type = type;
    objectPool->canFreeObjects = canFreeObjects;
    ArrayList_fill(&objectPool->availableObjects, size, &vkNullObject);
    return objectPool;
}

VkObject* VkObjectPool_get(VkObjectPool* objectPool) {
    VkObject* availableObject;
    if (objectPool->canFreeObjects) {
        if (objectPool->availableObjects.size == 0) return NULL;
        availableObject = ArrayList_removeAt(&objectPool->availableObjects, objectPool->availableObjects.size-1);
        if (VKOBJECT_IS_NULL(availableObject)) availableObject = VkObject_create(objectPool->type, VKOBJECT_NULL_ID);
        ArrayList_add(&objectPool->usedObjects, availableObject);
    }
    else {
        if (objectPool->position == objectPool->availableObjects.size) return NULL;
        availableObject = objectPool->availableObjects.elements[objectPool->position];
        if (VKOBJECT_IS_NULL(availableObject)) {
            availableObject = VkObject_create(objectPool->type, VKOBJECT_NULL_ID);
            objectPool->availableObjects.elements[objectPool->position] = availableObject;
        }
    }
    objectPool->position++;
    return availableObject;
}

void VkObjectPool_reset(VkObjectPool* objectPool) {
    objectPool->position = 0;
    if (objectPool->canFreeObjects) {
        while (objectPool->usedObjects.size > 0) {
            VkObject* usedObject = ArrayList_removeAt(&objectPool->usedObjects, objectPool->usedObjects.size-1);
            ArrayList_add(&objectPool->availableObjects, usedObject);
        }
    }
}

void VkObjectPool_destroy(VkObjectPool* objectPool) {
    freeArrayListElements(&objectPool->usedObjects);
    freeArrayListElements(&objectPool->availableObjects);
    free(objectPool);
}

void VkObjectPool_freeObject(VkObjectPool* objectPool, VkObject* usedObject) {
    if (!objectPool->canFreeObjects) return;
    ArrayList_remove(&objectPool->usedObjects, usedObject);
    ArrayList_add(&objectPool->availableObjects, usedObject);
}