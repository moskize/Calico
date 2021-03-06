package com.taozeyu.calico.generator;

import java.io.IOException;
import java.io.Reader;

class HtmlTemplateReader extends AllowFillReader {

	// Nashorn 似乎对于 JavaScript 代码每一行字符数有限制（超出后将忽视），因此必须将过长的 print 语句拆成多行。
	private static final int PrintCharNumLimit = 500;
	
	private static final String PrintStringHead = "print('";
	private static final String PrintStringTail = "');\n";
	
	private static final String PrintExpressionHead = "print(";
	private static final String PrintExpressionTail = ");\n";
	
	private final StringEscapeReader reader;
	private State state = State.Content;
	
	private boolean hasPrintTail = false;
	
	private static enum State {
		Content, InvokeScript, PrintScript;
	}
	
	HtmlTemplateReader(Reader reader) {
		this.reader = new StringEscapeReader(reader);
		this.reader.fillContent(PrintStringHead);
		this.reader.setEscapeFlag(true);
	}

	@Override
	protected int readOneChar() throws IOException {
		int ch = reader.read();
		if(ch < 0) {
			ch = fillTailCodeIfNeverFill(ch);
			return ch;
		}
		switch(state) {
		case Content:
			ch = handleCharWhenIsContent(ch);
			break;
			
		case InvokeScript:
			ch = handleCharWhenIsScript(ch);
			break;
			
		case PrintScript:
			ch = handleCharWhenIsScript(ch);
			break;
		}
		return ch;
	}

	private int fillTailCodeIfNeverFill(int ch) throws IOException {
		if(!hasPrintTail) {
			if(state != State.Content) {
				throw new IOException();
			}
			reader.setEscapeFlag(false);
			reader.fillContent(PrintStringTail);
			ch = reader.read();
			
			hasPrintTail = true;
		}
		return ch;
	}

	private int handleCharWhenIsContent(int ch) throws IOException {
		
		if(ch == '<' && (ch = reader.read()) >= 0) {
			if(ch == '%' && (ch = reader.read()) >= 0) {
				ch = gotoScriptState(ch);
				
			} else {
				reader.fillChar((char) ch);
				ch = '<';
			}
		} else if(isReadNumOverLimit()) {
			createNewPrintLine();
		}
		return ch;
	}

	private boolean isReadNumOverLimit() {
		return reader.getRealReadCharCount() >= PrintCharNumLimit;
	}
	
	private void createNewPrintLine() {
		reader.resetRealReadCharCount();
		reader.setEscapeFlag(false);
		reader.fillContent("\\\n");
		reader.setEscapeFlag(true);
	}

	private int gotoScriptState(int ch) throws IOException {
		reader.setEscapeFlag(false);
		reader.fillContent(PrintStringTail);
		if(ch == '=') {
			state = State.PrintScript;
			reader.fillContent(PrintExpressionHead);
		} else {
			state = State.InvokeScript;
			reader.fillChar((char) ch);
		}
		ch = reader.read();
		return ch;
	}

	private int handleCharWhenIsScript(int ch) throws IOException {
		if(ch == '%' && (ch = reader.read()) >= 0) {
			if(ch == '>') {
				if(state == State.PrintScript) {
					reader.fillContent(PrintExpressionTail);
				}
				reader.fillContent(PrintStringHead);
				reader.setEscapeFlag(true);
				state = State.Content;
				ch = reader.read();
				
			} else {
				reader.fillChar((char) ch);
				ch = '%';
			}
		}
		return ch;
	}
	
	@Override
	public void close() throws IOException {
		reader.close();
	}

}
