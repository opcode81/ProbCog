/*******************************************************************************
 * Copyright (C) 2009-2012 Dominik Jain, Paul Maier.
 * 
 * This file is part of ProbCog.
 * 
 * ProbCog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProbCog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProbCog. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package probcog.bayesnets.core.io;

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
