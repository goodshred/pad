package com.github.emailtohl.lib.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;

/**
 * 读取流，自动检测流中的文本编码
 * @author HeLei
 */
public final class TextUtil {
	private static final Logger LOG = LogManager.getLogger();

	/**
	 * 从输入流中检测内容编码格式，jchardet是mozilla自动字符集探测算法代码
	 * @param inputStream 文本流
	 * @return 探测该文本流的编码集，若没有找出编码集，则返回null
	 */
	public static String detect(InputStream inputStream) {
		class Detector implements nsICharsetDetectionObserver {
			boolean found = false;
			String charset = null;
			
			@Override
			public void Notify(String charset) {
				found = true;
				this.charset = charset;
				LOG.debug("charset maybe: " + charset);
			}
			
			String detect(InputStream inputStream) {
				nsDetector det = new nsDetector(nsPSMDetector.ALL);
				det.Init(this);
				byte[] bytes = new byte[512];
				int len;
				try {
					while ((len = inputStream.read(bytes, 0, bytes.length)) != -1 && !this.found) {
						det.DoIt(bytes, len, false);
					}
				} catch (IOException e) {
					LOG.catching(e);
				}
				det.DataEnd();
				String[] probableCharsets = det.getProbableCharsets();
				if (LOG.isDebugEnabled()) {
					LOG.debug("The charset might be: ");
					LOG.debug(join(probableCharsets));
				}
				if (this.charset != null) {
					return this.charset;
				}
				if (probableCharsets.length == 0 || "nomatch".equals(probableCharsets[0])) {
					return null;
				}
				Random r = new Random();
				// 从可能的编码中随机返回一个
				return probableCharsets[r.nextInt(probableCharsets.length)];
			}
			
		}
		return new Detector().detect(inputStream);
	}
	/**
	 * 猜测文件的编码格式，并读取内容，若读取失败或无法识别编码，则返回空字符串
	 * 
	 * @param inputStream 文本流
	 * @return 根据探测的编码集，将该文本流解析为文本，如果执行失败，则返回空字符串
	 */
	public static String readFileToString(InputStream inputStream) {
		ByteArrayOutputStream out = null;
		ByteArrayInputStream in = null;
		try {
			out = new ByteArrayOutputStream();
			byte[] bytes = new byte[512];
			int i;
			while (true) {
				i = inputStream.read(bytes);
				if (i == -1) {
					break;
				} else {
					out.write(bytes, 0, i);
				}
			}
			in = new ByteArrayInputStream(bytes);
			String encoding = detect(in);
			if (encoding == null) {
				return "";
			}
			Charset charset = Charset.forName(encoding);
			ByteBuffer bbuf = ByteBuffer.wrap(out.toByteArray());
			CharBuffer cbuf = charset.decode(bbuf);
			return cbuf.toString();
		} catch (IOException e) {
			LOG.catching(e);
			return "";
		} catch (UnsupportedCharsetException e) {
			LOG.trace(e);
			return "";
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					LOG.catching(e);
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					LOG.catching(e);
				}
			}
		}
	}
	
	public static String join(String[] arr) {
		boolean first = true;
		StringBuilder s = new StringBuilder();
		for (String c : arr) {
			if (first) {
				s.append(c);
				first = false;
			} else {
				s.append(',').append(c);
			}
		}
		return s.toString();
	}
}