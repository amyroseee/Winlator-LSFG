#ifndef GLADIO_GL_CONTEXT_H
#define GLADIO_GL_CONTEXT_H

#include "gl_client_state.h"

typedef struct __GLXcontextRec {
    Display* dpy;
    int id;
    GLXDrawable drawable;
    GLClientState clientState;
} __GLXcontextRec;

typedef struct CommandBuffer {
    bool begin;
    GLenum mode;
    int minIndex;
    int maxIndex;
    ArrayBuffer data;
} CommandBuffer;

typedef struct GLContext {
    int id;
    RingBuffer* serverRing;
    RingBuffer* clientRing;
    GLClientState* clientState;
    GLXContext glxContext;
    CommandBuffer commandBuffer;
    uint8_t lockCount;
} GLContext;

typedef struct AttributePair {
    int name;
    int value;
} AttributePair;

typedef struct __GLXFBConfigRec {
    AttributePair attributes[16];
    int numProperties;
} __GLXFBConfigRec;

extern bool setupRingBuffers(GLContext* context);
extern bool gladioInitOnce();
extern char* getCachedString(int name);
extern char* putCachedString(int name, char* string, int length);
extern void writeUnboundVertexArrays(GLint first, GLsizei count, const void* indices, GLenum indexType, GLint basevertex);
extern int serverFd;
extern ArrayBuffer outputBuffer;
extern ArrayBuffer inputBuffer;
extern SparseArray glxContexts;
extern thread_local GLContext* currentGLContext;

extern pthread_mutex_t gl_call_mutex;

#define GLX_OPCODE_CREATE_GL_CONTEXT 1
#define GLX_OPCODE_DESTROY_GL_CONTEXT 2
#define GLX_OPCODE_CREATE_CONTEXT 3
#define GLX_OPCODE_DESTROY_CONTEXT 4
#define GLX_OPCODE_QUERY_VERSION 7
#define GLX_OPCODE_QUERY_EXTENSIONS_STRING 18
#define GLX_OPCODE_QUERY_SERVER_STRING 19
#define GLX_OPCODE_GET_FB_CONFIGS 21
#define GLX_OPCODE_CREATE_CONTEXT_ATTRIBS_ARB 34

#define GL_CALL_LOCK() if (currentGLContext->lockCount++ == 0) pthread_mutex_lock(&gl_call_mutex)
#define GL_CALL_UNLOCK() if (--currentGLContext->lockCount == 0) pthread_mutex_unlock(&gl_call_mutex)
        
static inline GLXContext createGLXContext(Display* dpy, int contextId, GLXContext sharedContext) {
    GLXContext context = calloc(1, sizeof(__GLXcontextRec));
    context->dpy = dpy;
    context->id = contextId;
    GLVertexArrayObject_setBound(&context->clientState, 0);
    GLClientState_init(&context->clientState, sharedContext ? &sharedContext->clientState : NULL);
    
    SparseArray_put(&glxContexts, contextId, context);
    return context;
}

static inline void createGLContextForCallingThread() {
    if (currentGLContext) return;
    int threadId = currentThreadId();
    glx_send(serverFd, GLX_OPCODE_CREATE_GL_CONTEXT, &threadId, sizeof(int));

    GLContext* context = calloc(1, sizeof(GLContext));
    context->id = threadId;

    if (!setupRingBuffers(context)) {
        free(context);
        context = NULL;
    }
    currentGLContext = context;
}

static inline void destroyGLContextForCallingThread() {
    if (!currentGLContext) return;
    if (!glx_send(serverFd, GLX_OPCODE_DESTROY_GL_CONTEXT, &currentGLContext->id, sizeof(int))) return;

    ArrayBuffer replyData = {0};
    if (!glx_recv(serverFd, &replyData)) return;

    if (currentGLContext->serverRing) RingBuffer_free(currentGLContext->serverRing);
    if (currentGLContext->clientRing) RingBuffer_free(currentGLContext->clientRing);
    free(currentGLContext);
    currentGLContext = NULL;
}

#endif