#define _GNU_SOURCE
#include <sys/mman.h>

#include "gladio.h"
#include "gl_context.h"

extern void getRangeIndices(const void* indices, GLsizei count, GLenum indexType, GLint basevertex, GLuint* range);

GLBuffer* GLBuffer_getBound(GLenum target) {
    GLClientState* clientState = currentGLContext->clientState;
    return clientState->vao->buffer[indexOfGLTarget(target)];
}

void GLBuffer_setBound(GLenum target, GLuint id) {
    GLClientState* clientState = currentGLContext->clientState;
    
    GLBuffer* buffer = NULL;
    if (id > 0) {
        buffer = SparseArray_get(clientState->buffers, id);
        if (!buffer) {
            buffer = calloc(1, sizeof(GLBuffer));
            buffer->id = id;
            buffer->type = target;
            SparseArray_put(clientState->buffers, id, buffer);
        }
    }
    
    clientState->vao->buffer[indexOfGLTarget(target)] = buffer;
}

GLenum GLBuffer_getType(GLuint id) {
    GLBuffer* buffer = SparseArray_get(currentGLContext->clientState->buffers, id);
    return buffer ? buffer->type : GL_NONE;
}

static void unmapBuffer(GLBuffer* buffer) {
    if (buffer->mappedData) {
        munmap(buffer->mappedData, buffer->size);
        buffer->mappedData = NULL;
        buffer->size = 0;
    }
}

bool GLBuffer_mapMemory(GLBuffer* buffer, int fd, int size) {
    unmapBuffer(buffer);
    
    void* mappedData = mmap(NULL, size, PROT_WRITE | PROT_READ, MAP_SHARED, fd, 0);
    if (mappedData == MAP_FAILED) return false;
    memset(mappedData, 0, size);

    CLOSEFD(fd);
    buffer->mappedData = mappedData;
    buffer->size = size;
    return true;
}

void GLBuffer_delete(GLuint id) {
    GLBuffer* buffer = SparseArray_get(currentGLContext->clientState->buffers, id);
    if (buffer) {
        GLClientState* clientState = currentGLContext->clientState;
        for (int i = 0; i < clientState->vertexArrays.size; i++) {
            GLVertexArrayObject* vbo = clientState->vertexArrays.entries[i].value;
            for (int j = 0; j < MAX_BUFFER_TARGETS; j++) if (vbo->buffer[j] == buffer) vbo->buffer[j] = NULL;
        }
        for (int i = 0; i < MAX_BUFFER_TARGETS; i++) {
            if (clientState->defaultVAO.buffer[i] == buffer) clientState->defaultVAO.buffer[i] = NULL;
        }        
        
        ArrayList_remove(&clientState->persistentBuffers, buffer);
        unmapBuffer(buffer);
        MEMFREE(buffer);
        SparseArray_remove(currentGLContext->clientState->buffers, id);
    }
}

void GLBuffer_onDestroy(GLClientState* clientState) {
    for (int i = clientState->buffers->size-1; i >= 0; i--) {
        GLBuffer* buffer = clientState->buffers->entries[i].value;
        unmapBuffer(buffer);
        MEMFREE(buffer);
        SparseArray_removeAt(clientState->buffers, i);
    }
}

void GLBuffer_getParamsv(GLenum target, GLenum pname, GLint* params) {
    GLBuffer* buffer = GLBuffer_getBound(target);
    if (!buffer) {
        *params = 0;
        return;
    }
    
    switch (pname) {
        case GL_BUFFER_ACCESS:
        case GL_BUFFER_ACCESS_FLAGS:
            *params = buffer->access;
            break;
        case GL_BUFFER_IMMUTABLE_STORAGE:
        case GL_BUFFER_STORAGE_FLAGS:
            *params = 0;
            break;
        case GL_BUFFER_MAPPED:
            *params = buffer->mapped ? GL_TRUE : GL_FALSE;
            break;
        case GL_BUFFER_MAP_LENGTH:
            *params = buffer->mapLength;
            break; 
        case GL_BUFFER_MAP_OFFSET:
            *params = buffer->mapOffset;
            break;
        case GL_BUFFER_SIZE:
            *params = buffer->size;
            break;
        case GL_BUFFER_USAGE:
            *params = buffer->usage;
            break;            
    }
}

void flushMappedPersistentBuffers(GLint first, GLsizei count, const void* indices, GLenum indexType, GLint basevertex) {
    if (currentGLContext->clientState->persistentBuffers.size == 0) return;
    
    if (indexType != GL_NONE) {
        GLuint range[2];
        getRangeIndices(indices, count, indexType, basevertex, range);
        first = range[0];
        count = (range[1] - range[0]) + 1;
    }
    
    GLClientState* clientState = currentGLContext->clientState;
    for (int i = 0; i < clientState->persistentBuffers.size; i++) {
        GLBuffer* buffer = clientState->persistentBuffers.elements[i];
        if (buffer->mapped) {
            int offset = first * buffer->drawStride + buffer->mapOffset;
            int length = count * buffer->drawStride;
            
            glFlushMappedBufferRange(buffer->type, offset, length);
        }
    }
}