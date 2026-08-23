#ifndef GLADIO_GL_DSA_H
#define GLADIO_GL_DSA_H

#include "gladio.h"

#define GL_DSA_SAVE_ACTIVE_TEXTURE(unit) \
    GL_CALL_LOCK(); \
    GLuint oldUnit = GL_TEXTURE0 + currentGLContext->clientState->activeTexture; \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_ACTIVE_TEXTURE, &unit, sizeof(GLuint))
    
#define GL_DSA_RESTORE_ACTIVE_TEXTURE() \
    currentGLContext->clientState->activeTexture = oldUnit - GL_TEXTURE0; \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_ACTIVE_TEXTURE, NULL, 0); \
    GL_CALL_UNLOCK()

#define GL_DSA_SAVE_BOUND_TEXTURE(texture) \
    GL_CALL_LOCK(); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_BOUND_TEXTURE, &texture, sizeof(GLuint))
    
#define GL_DSA_RESTORE_BOUND_TEXTURE() \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_BOUND_TEXTURE, NULL, 0); \
    GL_CALL_UNLOCK()

#define GL_DSA_SAVE_BOUND_BUFFER(buffer) \
    GL_CALL_LOCK(); \
    GLenum target = GLBuffer_getType(buffer); \
    GLBuffer* oldBuffer = GLBuffer_getBound(target); \
    GLBuffer_setBound(target, buffer); \
    uint64_t requestData = PACK32(target, buffer); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_BOUND_BUFFER, &requestData, sizeof(uint64_t))
    
#define GL_DSA_RESTORE_BOUND_BUFFER() \
    GLBuffer_setBound(target, oldBuffer ? oldBuffer->id : 0); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_BOUND_BUFFER, NULL, 0); \
    GL_CALL_UNLOCK()
    
#define GL_DSA_SAVE_BOUND_ARB_PROGRAM(program) \
    GL_CALL_LOCK(); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_BOUND_ARB_PROGRAM, &program, sizeof(GLuint))
    
#define GL_DSA_RESTORE_BOUND_ARB_PROGRAM() \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_BOUND_ARB_PROGRAM, NULL, 0); \
    GL_CALL_UNLOCK()

#define GL_DSA_SAVE_BOUND_FRAMEBUFFER(framebuffer) \
    GL_CALL_LOCK(); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_BOUND_FRAMEBUFFER, &framebuffer, sizeof(GLuint))
    
#define GL_DSA_RESTORE_BOUND_FRAMEBUFFER() \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_BOUND_FRAMEBUFFER, NULL, 0); \
    GL_CALL_UNLOCK()
    
#define GL_DSA_SAVE_BOUND_RENDERBUFFER(renderbuffer) \
    GL_CALL_LOCK(); \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_SAVE_BOUND_RENDERBUFFER, &renderbuffer, sizeof(GLuint))
    
#define GL_DSA_RESTORE_BOUND_RENDERBUFFER() \
    gl_send(currentGLContext->serverRing, REQUEST_CODE_GL_DSA_RESTORE_BOUND_RENDERBUFFER, NULL, 0); \
    GL_CALL_UNLOCK()

#endif