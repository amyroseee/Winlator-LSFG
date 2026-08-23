#ifndef GLADIO_GL_BUFFER_H
#define GLADIO_GL_BUFFER_H

#include <sys/mman.h>

#include "GL/gl.h"

typedef struct GLBuffer {
    GLuint id;
    GLenum type;
    void* mappedData;
    int size;
    int mapOffset;
    int mapLength;
    int access;
    int usage;
    short drawStride;
    bool mapped;
} GLBuffer;

extern GLBuffer* GLBuffer_getBound(GLenum target);
extern void GLBuffer_setBound(GLenum target, GLuint id);
extern GLenum GLBuffer_getType(GLuint id);
extern bool GLBuffer_mapMemory(GLBuffer* buffer, int fd, int size);
extern void GLBuffer_delete(GLuint id);
extern void GLBuffer_onDestroy(GLClientState* clientState);
extern void GLBuffer_getParamsv(GLenum target, GLenum pname, GLint* params);

extern void flushMappedPersistentBuffers(GLint first, GLsizei count, const void* indices, GLenum indexType, GLint basevertex);

#endif