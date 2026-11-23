"""Writes warsmash/backend/java/wsnx/nx/NxGL30.java: gdx's GL30 for the Switch 2 (desktop GL 3.3
core through nxgl.h), as Clearwing inline natives. SwitchGL (upstream, GL20) stays the base class.

Buffers go to the natives as Object: the transpiler would otherwise convert a Buffer argument to
its base address itself, without the position (LWJGL, which gdx's desktop GL follows, starts at the
position) and dereferencing it even when it is null. A plain Buffer also carries its element size
from Java (nxSize).

    python -I scripts/gen_nxgl30.py
"""
import re
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "warsmash/backend/java/wsnx/nx/NxGL30.java"

# The 96 methods GL30 declares beyond GL20 (gdx 1.13.1), in the order of DumpGL30's listing.
# Argument types: i int, f float, z boolean, IB/FB/LB IntBuffer/FloatBuffer/LongBuffer, B Buffer,
# ia int[] (followed by its offset), S String, O int used as a pointer offset into a bound buffer.
SIGS = """
v glBeginQuery i:target i:id
v glBeginTransformFeedback i:primitiveMode
v glBindBufferBase i:target i:index i:buffer
v glBindBufferRange i:target i:index i:buffer i:offset i:size
v glBindSampler i:unit i:sampler
v glBindTransformFeedback i:target i:id
v glBindVertexArray i:array
v glBlitFramebuffer i:srcX0 i:srcY0 i:srcX1 i:srcY1 i:dstX0 i:dstY0 i:dstX1 i:dstY1 i:mask i:filter
v glClearBufferfi i:buffer i:drawbuffer f:depth i:stencil
v glClearBufferfv i:buffer i:drawbuffer FB:value
v glClearBufferiv i:buffer i:drawbuffer IB:value
v glClearBufferuiv i:buffer i:drawbuffer IB:value
v glCopyBufferSubData i:readTarget i:writeTarget i:readOffset i:writeOffset i:size
v glCopyTexSubImage3D i:target i:level i:xoffset i:yoffset i:zoffset i:x i:y i:width i:height
v glDeleteQueries i:n IB:ids
v glDeleteQueries i:n ia:ids i:offset
v glDeleteSamplers i:count IB:samplers
v glDeleteSamplers i:count ia:samplers i:offset
v glDeleteTransformFeedbacks i:n IB:ids
v glDeleteTransformFeedbacks i:n ia:ids i:offset
v glDeleteVertexArrays i:n IB:arrays
v glDeleteVertexArrays i:n ia:arrays i:offset
v glDrawArraysInstanced i:mode i:first i:count i:instanceCount
v glDrawBuffers i:n IB:bufs
v glDrawElementsInstanced i:mode i:count i:type O:indicesOffset i:instanceCount
v glDrawRangeElements i:mode i:start i:end i:count i:type B:indices
v glDrawRangeElements i:mode i:start i:end i:count i:type O:offset
v glEndQuery i:target
v glEndTransformFeedback
v glFlushMappedBufferRange i:target i:offset i:length
v glFramebufferTextureLayer i:target i:attachment i:texture i:level i:layer
v glGenQueries i:n IB:ids
v glGenQueries i:n ia:ids i:offset
v glGenSamplers i:count IB:samplers
v glGenSamplers i:count ia:samplers i:offset
v glGenTransformFeedbacks i:n IB:ids
v glGenTransformFeedbacks i:n ia:ids i:offset
v glGenVertexArrays i:n IB:arrays
v glGenVertexArrays i:n ia:arrays i:offset
v glGetActiveUniformBlockiv i:program i:uniformBlockIndex i:pname IB:params
v glGetActiveUniformsiv i:program i:uniformCount IB:uniformIndices i:pname IB:params
v glGetBufferParameteri64v i:target i:pname LB:params
i glGetFragDataLocation i:program S:name
v glGetInteger64v i:pname LB:params
v glGetQueryObjectuiv i:id i:pname IB:params
v glGetQueryiv i:target i:pname IB:params
v glGetSamplerParameterfv i:sampler i:pname FB:params
v glGetSamplerParameteriv i:sampler i:pname IB:params
i glGetUniformBlockIndex i:program S:uniformBlockName
v glGetUniformuiv i:program i:location IB:params
v glGetVertexAttribIiv i:index i:pname IB:params
v glGetVertexAttribIuiv i:index i:pname IB:params
v glInvalidateFramebuffer i:target i:numAttachments IB:attachments
v glInvalidateSubFramebuffer i:target i:numAttachments IB:attachments i:x i:y i:width i:height
z glIsQuery i:id
z glIsSampler i:sampler
z glIsTransformFeedback i:id
z glIsVertexArray i:array
v glPauseTransformFeedback
v glProgramParameteri i:program i:pname i:value
v glReadBuffer i:mode
v glRenderbufferStorageMultisample i:target i:samples i:internalformat i:width i:height
v glResumeTransformFeedback
v glSamplerParameterf i:sampler i:pname f:param
v glSamplerParameterfv i:sampler i:pname FB:param
v glSamplerParameteri i:sampler i:pname i:param
v glSamplerParameteriv i:sampler i:pname IB:param
v glTexImage2D i:target i:level i:internalformat i:width i:height i:border i:format i:type O:offset
v glTexImage3D i:target i:level i:internalformat i:width i:height i:depth i:border i:format i:type B:pixels
v glTexImage3D i:target i:level i:internalformat i:width i:height i:depth i:border i:format i:type O:offset
v glTexSubImage2D i:target i:level i:xoffset i:yoffset i:width i:height i:format i:type O:offset
v glTexSubImage3D i:target i:level i:xoffset i:yoffset i:zoffset i:width i:height i:depth i:format i:type B:pixels
v glTexSubImage3D i:target i:level i:xoffset i:yoffset i:zoffset i:width i:height i:depth i:format i:type O:offset
v glUniform1uiv i:location i:count IB:value
v glUniform3uiv i:location i:count IB:value
v glUniform4uiv i:location i:count IB:value
v glUniformBlockBinding i:program i:uniformBlockIndex i:uniformBlockBinding
v glUniformMatrix2x3fv i:location i:count z:transpose FB:value
v glUniformMatrix2x4fv i:location i:count z:transpose FB:value
v glUniformMatrix3x2fv i:location i:count z:transpose FB:value
v glUniformMatrix3x4fv i:location i:count z:transpose FB:value
v glUniformMatrix4x2fv i:location i:count z:transpose FB:value
v glUniformMatrix4x3fv i:location i:count z:transpose FB:value
z glUnmapBuffer i:target
v glVertexAttribDivisor i:index i:divisor
v glVertexAttribI4i i:index i:x i:y i:z i:w
v glVertexAttribI4ui i:index i:x i:y i:z i:w
v glVertexAttribIPointer i:index i:size i:type i:stride O:offset
"""

JAVA_TYPE = {"i": "int", "O": "int", "f": "float", "z": "boolean", "IB": "IntBuffer",
             "FB": "FloatBuffer", "LB": "LongBuffer", "B": "Buffer", "ia": "int[]", "S": "String"}
RET = {"v": "void", "i": "int", "z": "boolean"}
ELEMENT = {"IB": 4, "FB": 4, "LB": 8}
C_PTR = {"IB": "GLint *", "FB": "GLfloat *", "LB": "GLint64 *"}
# GL's unsigned-int-pointer parameters (the casts matter in C++).
UNSIGNED = ("glClearBufferuiv", "glGetQueryObjectuiv", "glGetUniformuiv", "glGetVertexAttribIuiv",
            "glUniform1uiv", "glUniform3uiv", "glUniform4uiv", "glDrawBuffers", "glInvalidateFramebuffer",
            "glInvalidateSubFramebuffer", "glGen", "glDelete")


def parse():
    out = []
    for line in SIGS.strip().splitlines():
        ret, name, *args = line.split()
        out.append((ret, name, [tuple(a.split(":")) for a in args]))
    # + 7 written by hand (SPECIAL); glVertexAttribPointer(..., Buffer) is SwitchGL's (GL20)
    assert len(out) == 88, len(out)
    return out


def method(ret, name, args):
    jargs = ", ".join(f"{JAVA_TYPE[t]} {n}" for t, n in args)
    has_buffer = any(t in ("IB", "FB", "LB", "B") for t, _ in args)
    # C call arguments
    cargs = []
    for k, (t, n) in enumerate(args):
        if t in ("i", "f"):
            cargs.append(n)
        elif t == "z":
            cargs.append(f"(GLboolean) {n}")
        elif t == "O":
            cargs.append(f"(const void *) (intptr_t) {n}")
        elif t in ELEMENT:
            ptr = C_PTR[t]
            if t == "IB" and (name.startswith(UNSIGNED) or name.startswith(("glGen", "glDelete"))):
                ptr = "GLuint *"
            if t == "IB" and name in ("glGetActiveUniformsiv",) and n == "uniformIndices":
                ptr = "const GLuint *"
            cargs.append(f"({ptr}) nxAddr({n}, {ELEMENT[t]})")
        elif t == "B":
            cargs.append(f"nxAddr({n}, {n}Size)")
        elif t == "ia":
            cargs.append(f"(GLuint *) ({n} + offset)")
        elif t == "S":
            cargs.append(n)
    # glGen*/glDelete* (n, int[], offset): the offset is folded into the pointer
    if any(t == "ia" for t, _ in args):
        cargs = [c for c, (t, n) in zip(cargs, args) if not (n == "offset")]
    call = f"{name}({', '.join(cargs)})"
    if ret == "v":
        body = f"{call};"
    elif ret == "i":
        body = f"return (jint) {call};"
    else:
        body = f"return {call} ? 1 : 0;"
    jret = RET[ret]
    if not has_buffer:
        return f"""	@Override
	public native {jret} {name} ({jargs}); /*
		{body}
	*/
"""
    # Public wrapper + private native taking Objects
    nargs, pass_ = [], []
    for t, n in args:
        if t in ("IB", "FB", "LB"):
            nargs.append(f"Object {n}")
            pass_.append(n)
        elif t == "B":
            nargs.append(f"Object {n}, int {n}Size")
            pass_.append(f"{n}, nxSize({n})")
        else:
            nargs.append(f"{JAVA_TYPE[t]} {n}")
            pass_.append(n)
    ret_kw = "" if ret == "v" else "return "
    return f"""	@Override
	public {jret} {name} ({jargs}) {{
		{ret_kw}{name}0({', '.join(pass_)});
	}}

	private static native {jret} {name}0 ({', '.join(nargs)}); /*
		{body}
	*/
"""


HEADER = '''package wsnx.nx;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.thelogicmaster.switchgdx.SwitchGL;

/**
 * Generated by scripts/gen_nxgl30.py: do not edit. gdx's GL30 on the Switch 2's desktop GL 3.3 core
 * context (nxgl.h loads the entry points through eglGetProcAddress). GL20 is SwitchGDX's SwitchGL.
 * Buffers are read from their position, as LWJGL does.
 */
public class NxGL30 extends SwitchGL implements GL30 {
	/*JNI
	#include "nxgl.h"
	#include <java/nio/ByteBuffer.h>
	#include <vector>

	// A buffer argument's address at its position (null for a null buffer).
	static inline void *nxAddr(jobject buffer, jint elementSize) {
		if (!buffer)
			return nullptr;
		auto b = (java_nio_Buffer *) buffer;
		return (char *) (intptr_t) b->F_address + (intptr_t) elementSize * b->F_position;
	}
	*/

	private static int nxSize (Buffer buffer) {
		if (buffer == null || buffer instanceof ByteBuffer)
			return 1;
		if (buffer instanceof ShortBuffer || buffer instanceof CharBuffer)
			return 2;
		if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer)
			return 4;
		if (buffer instanceof LongBuffer || buffer instanceof DoubleBuffer)
			return 8;
		throw new GdxRuntimeException("unknown buffer type " + buffer.getClass());
	}

'''

SPECIAL = '''	@Override
	public String glGetActiveUniformBlockName (int program, int uniformBlockIndex) {
		return glGetActiveUniformBlockName0(program, uniformBlockIndex);
	}

	private static native String glGetActiveUniformBlockName0 (int program, int uniformBlockIndex); /*
		GLint length = 0;
		glGetActiveUniformBlockiv(program, uniformBlockIndex, GL_UNIFORM_BLOCK_NAME_LENGTH, &length);
		std::vector<GLchar> name(length > 0 ? length : 1, 0);
		glGetActiveUniformBlockName(program, uniformBlockIndex, (GLsizei) name.size(), nullptr, name.data());
		return (jobject) stringFromNative(ctx, name.data());
	*/

	@Override
	public void glGetActiveUniformBlockName (int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {
		glGetActiveUniformBlockNameB0(program, uniformBlockIndex, length, uniformBlockName, uniformBlockName.remaining());
	}

	private static native void glGetActiveUniformBlockNameB0 (int program, int uniformBlockIndex, Object length, Object name, int size); /*
		glGetActiveUniformBlockName(program, uniformBlockIndex, size, (GLsizei *) nxAddr(length, 4), (GLchar *) nxAddr(name, 1));
	*/

	@Override
	public Buffer glGetBufferPointerv (int target, int pname) {
		throw new GdxRuntimeException("glGetBufferPointerv is not supported (as on gdx's LWJGL backends)");
	}

	@Override
	public native String glGetStringi (int name, int index); /*
		auto s = (const char *) glGetStringi(name, index);
		return s ? (jobject) stringFromNative(ctx, s) : nullptr;
	*/

	@Override
	public void glGetUniformIndices (int program, String[] uniformNames, IntBuffer uniformIndices) {
		glGetUniformIndices0(program, uniformNames, uniformIndices);
	}

	private static native void glGetUniformIndices0 (int program, String[] names, Object indices); /*
		auto array = (jarray) names;
		std::vector<const GLchar *> pointers(array->length);
		for (int i = 0; i < array->length; i++)
			pointers[i] = stringToNative(ctx, (jstring) ((jobject *) array->data)[i]);
		glGetUniformIndices(program, array->length, pointers.data(), (GLuint *) nxAddr(indices, 4));
	*/

	@Override
	public Buffer glMapBufferRange (int target, int offset, int length, int access) {
		ByteBuffer buffer = glMapBufferRange0(target, offset, length, access);
		return buffer == null ? null : buffer.order(ByteOrder.nativeOrder());
	}

	private static native ByteBuffer glMapBufferRange0 (int target, int offset, int length, int access); /*
		void *pointer = glMapBufferRange(target, offset, length, access);
		if (!pointer)
			return nullptr;
		auto buffer = gcAllocProtected(ctx, &class_java_nio_ByteBuffer);
		init_java_nio_ByteBuffer_long_int(ctx, buffer, (jlong) (intptr_t) pointer, length);
		unprotectObject(buffer);
		return buffer;
	*/

	@Override
	public void glTransformFeedbackVaryings (int program, String[] varyings, int bufferMode) {
		glTransformFeedbackVaryings0(program, varyings, bufferMode);
	}

	private static native void glTransformFeedbackVaryings0 (int program, String[] varyings, int bufferMode); /*
		auto array = (jarray) varyings;
		std::vector<const GLchar *> pointers(array->length);
		for (int i = 0; i < array->length; i++)
			pointers[i] = stringToNative(ctx, (jstring) ((jobject *) array->data)[i]);
		glTransformFeedbackVaryings(program, array->length, pointers.data(), bufferMode);
	*/
'''


def main():
    parts = [HEADER]
    for ret, name, args in parse():
        parts.append(method(ret, name, args))
        parts.append("\n")
    parts.append(SPECIAL)
    parts.append("}\n")
    text = "".join(parts)
    # warsmash-nx breadcrumbs: each native names itself in the log during frames 1 and 2 after
    # loading (nx_gltrace in nx_switchgdx.cpp), so a silent crash in the driver names its GL call.
    # Inline natives compile in NxGL30_native.cpp with C++ linkage, hence nx_gltrace_cpp.
    text = re.sub(r"(public native \w+ (gl\w+) \([^)]*\); /\*\n)",
                  lambda m: m.group(1) + '\t\tvoid nx_gltrace_cpp(const char *); nx_gltrace_cpp("' + m.group(2) + '");\n',
                  text)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8", newline="\n")
    print(f"{OUT}: {len(parse())} generated + 7 by hand")


main()
