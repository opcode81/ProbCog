package edu.tum.cs.bayesnets.core.io;

public class Converter_uai extends Converter_ergo {
	public Converter_uai() {
		this.isUAIstyle = true;
	}
	
	public String getDesc() {		
		return "UAI-Ergo";
	}

	public String getExt() {
		return "*.uai";
	}
}
