/*
 * Copyright (c) 2007-2018 Siemens AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 */

package com.siemens.ct.exi.grammars;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xerces.impl.xs.SchemaGrammar;
import org.apache.xerces.impl.xs.SubstitutionGroupHandler;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.models.CMBuilder;
import org.apache.xerces.impl.xs.models.CMNodeFactory;
import org.apache.xerces.impl.xs.models.XSCMValidator;
import org.apache.xerces.util.XMLResourceIdentifierImpl;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSWildcard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.siemens.ct.exi.core.exceptions.EXIException;
import com.siemens.ct.exi.core.grammars.event.AttributeGeneric;
import com.siemens.ct.exi.core.grammars.event.CharactersGeneric;
import com.siemens.ct.exi.core.grammars.event.EndElement;
import com.siemens.ct.exi.core.grammars.event.Event;
import com.siemens.ct.exi.core.grammars.event.StartElement;
import com.siemens.ct.exi.core.grammars.event.StartElementGeneric;
import com.siemens.ct.exi.core.grammars.event.StartElementNS;
import com.siemens.ct.exi.core.grammars.grammar.SchemaInformedElement;
import com.siemens.ct.exi.core.grammars.grammar.SchemaInformedGrammar;
import com.siemens.ct.exi.core.util.sort.QNameSort;
import com.siemens.ct.exi.core.util.sort.StartElementSort;

/**
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Richard.Kuntschke@siemens.com
 * 
 */

public abstract class EXIContentModelBuilder extends CMBuilder implements
		XMLErrorHandler {

	/** The logger used in this class. */
	private static final Logger LOGGER = LoggerFactory.getLogger(EXIContentModelBuilder.class);

	protected static final Event END_ELEMENT = new EndElement();
	protected static final Event START_ELEMENT_GENERIC = new StartElementGeneric();
	protected static final Event ATTRIBUTE_GENERIC = new AttributeGeneric();
	protected static final Event CHARACTERS_GENERIC = new CharactersGeneric();

	protected static final boolean forUPA = false;

	protected static final XSElementDeclarationSort elementDeclSort = new XSElementDeclarationSort();
	protected static final XSAttributeDeclarationSort attributeDeclSort = new XSAttributeDeclarationSort();
	protected static final XSAttributeUseSort attributeUseSort = new XSAttributeUseSort();
	protected static final StartElementSort startElementSort = new StartElementSort();

	protected static final QNameSort qnameSort = new QNameSort();

	protected SubstitutionGroupHandler subGroupHandler;

	protected XSModel xsModel;

	// errors while schema parsing
	protected List<String> schemaParsingErrors;

	// pool for element-declaration of StartElement events
	protected Map<XSElementDeclaration, StartElement> elementPool;

	public EXIContentModelBuilder() {
		super(new CMNodeFactory());
	}

	protected void initOnce() {
		elementPool = new HashMap<XSElementDeclaration, StartElement>();
		schemaParsingErrors = new ArrayList<String>();
	}

	protected void initEachRun() {
		elementPool.clear();
		schemaParsingErrors.clear();
	}

	public void loadGrammars(XMLInputSource xsdSource) throws EXIException {
		this.loadGrammars(xsdSource, null);
	}

	public void loadGrammars(XMLInputSource xsdSource,
			XMLEntityResolver entityResolver) throws EXIException {
		try {
			initEachRun();

			// load XSD schema & get XSModel
			XMLSchemaLoader sl = new XMLSchemaLoader();
			if (entityResolver != null) {
				sl.setEntityResolver(entityResolver);
			}
			sl.setErrorHandler(this);

			SchemaGrammar g = (SchemaGrammar) sl.loadGrammar(xsdSource);

			// set XSModel
			xsModel = g.toXSModel();

			// create substitution group-handler
			// NOTE: it is needed but not really used later on
			// (substitution groups are handled separately)
			// Xerces Version 2.9.1
			// XSGrammarBucket grammarBucket = new XSGrammarBucket();
			// grammarBucket.putGrammar(g, true);
			// subGroupHandler = new SubstitutionGroupHandler(grammarBucket);
			// Xerces Version 2.11.0
			subGroupHandler = new SubstitutionGroupHandler(sl);
		} catch (Exception e) {
			throw new EXIException("XML Schema document ("
					+ xsdSource.getSystemId() + ") not found.", e);
		}
	}

	public void loadXSDTypesOnlyGrammars() throws EXIException {
		String emptySchema = "<schema xmlns='http://www.w3.org/2001/XMLSchema' /> ";
		Reader r = new StringReader(emptySchema);
		// String publicId, String systemId, String baseSystemId, Reader
		// charStream, String encoding
		XMLInputSource is = new XMLInputSource(null, null, null, r, null);
		loadGrammars(is, null);
	}

	public void loadGrammars(String xsdLocation) throws EXIException {
		this.loadGrammars(xsdLocation, null);
	}

	public void loadGrammars(String xsdLocation,
			XMLEntityResolver entityResolver) throws EXIException {
		XMLInputSource xsdSource = null;

		if (entityResolver != null) {
			XMLResourceIdentifier rid = new XMLResourceIdentifierImpl();
			rid.setLiteralSystemId(xsdLocation);
			try {
				xsdSource = entityResolver.resolveEntity(rid);
			} catch (Exception e) {
			}
		}
		if (xsdSource == null) {
			String systemId = xsdLocation;
			String publicId = null;
			String baseSystemId = null; // f.getParent();
			xsdSource = new XMLInputSource(publicId, systemId, baseSystemId);
		}

		loadGrammars(xsdSource, entityResolver);
	}

	public void loadGrammars(InputStream xsdInputStream) throws EXIException {
		this.loadGrammars(xsdInputStream, null);
	}

	public void loadGrammars(InputStream xsdInputStream,
			XMLEntityResolver entityResolver) throws EXIException {
		// XSD source
		String publicId = null;
		String systemId = null;
		String baseSystemId = null;
		String encoding = null;
		XMLInputSource xsdSource = new XMLInputSource(publicId, systemId,
				baseSystemId, xsdInputStream, encoding);
		loadGrammars(xsdSource, entityResolver);
	}

	public XSModel getXSModel() {
		return this.xsModel;
	}

	// @Override
	// XSCMValidator createAllCM(XSParticleDecl particle) {
	// // Note: xsd:all is allowed to contain elements only
	// // maxOccurs: value must be 1
	// // minOccurs: value can be 0 or 1
	// assert (particle.getMaxOccurs() == 1);
	// assert (particle.getMinOccurs() == 0 || particle.getMinOccurs() == 1);
	//
	// throw new RuntimeException(
	// "All model group handling should not call createAllCM(...)");
	// // return super.createAllCM(particle);;
	// }

	private static SchemaInformedGrammar addNewState(
			Map<CMState, SchemaInformedGrammar> states, CMState key,
			boolean isMixedContent) {
		SchemaInformedGrammar val = new SchemaInformedElement();
		// is end
		if (key.end) {
			val.addTerminalProduction(END_ELEMENT);
		}
		// is mixed content
		if (isMixedContent) {
			val.addProduction(CHARACTERS_GENERIC, val);
		}
		states.put(key, val);

		return val;
	}

	private void getMaxOccursUnboundedElements(
			List<XSElementDeclaration> elementsMaxOccursUnbounded,
			XSParticle xsParticle) {
		getMaxOccursUnboundedElements(elementsMaxOccursUnbounded, xsParticle,
				false);
	}

	private void getMaxOccursUnboundedElements(
			List<XSElementDeclaration> elementsMaxOccursUnbounded,
			XSParticle xsParticle, boolean outerUnbounded) {
		XSTerm xsTerm = xsParticle.getTerm();

		if (xsTerm instanceof XSElementDeclaration) {
			XSElementDeclaration xse = (XSElementDeclaration) xsTerm;
			if ((outerUnbounded || xsParticle.getMaxOccursUnbounded())
					&& !elementsMaxOccursUnbounded.contains(xse)) {
				elementsMaxOccursUnbounded.add(xse);
			}
		} else if (xsTerm instanceof XSModelGroup) {
			XSModelGroup smg = (XSModelGroup) xsTerm;
			XSObjectList particles = smg.getParticles();
			for (int i = 0; i < particles.getLength(); i++) {
				XSParticle xsp = (XSParticle) particles.item(i);
				getMaxOccursUnboundedElements(elementsMaxOccursUnbounded, xsp,
						xsParticle.getMaxOccursUnbounded());
			}
		} else {
			// XSWildcard
		}
	}

	protected SchemaInformedGrammar handleParticle(XSComplexTypeDefinition ctd,
			boolean isMixedContent) throws EXIException {

		XSParticle xsParticle = ctd.getParticle();
		XSTerm xsTerm = xsParticle.getTerm();
		XSModelGroup mg;
		// special behavior for xsd:all
		if (xsTerm instanceof XSModelGroup
				&& (mg = (XSModelGroup) xsTerm).getCompositor() == XSModelGroup.COMPOSITOR_ALL) {
			// http://www.w3.org/TR/exi/#allGroupTerms
			// The grammar can accept any sequence of the given {particles}
			// in any order
			SchemaInformedGrammar allRule = new SchemaInformedElement();
			// EE
			allRule.addTerminalProduction(END_ELEMENT);
			// particles
			XSObjectList allParticles = mg.getParticles();
			for (int i = 0; i < allParticles.getLength(); i++) {
				XSObject o = allParticles.item(i);
				assert (o instanceof XSParticle);
				XSParticle xsp = (XSParticle) o;
				XSTerm tt = xsp.getTerm();
				// Note: xsd:all is allowed to contain elements only
				if (XSConstants.ELEMENT_DECLARATION == tt.getType()) {
					XSElementDeclaration el = (XSElementDeclaration) tt;
					// StartElement se = getStartElement(el);
					StartElement se = translatElementDeclarationToFSA(el);
					allRule.addProduction(se, allRule);
				} else {
					throw new RuntimeException(
							"No XSElementDeclaration for xsd:all particle, "
									+ tt);
				}
			}

			return allRule;
		} else {
			// complex types other than xsd:all model groups
			XSCMValidator xscmVal = getContentModel((XSComplexTypeDecl) ctd,
					forUPA);

			int[] state = xscmVal.startContentModel();
			@SuppressWarnings("unchecked")
			List<XSObject> possibleElements = xscmVal.whatCanGoHere(state);

			// elements that have a given maxOccurs unbounded
			List<XSElementDeclaration> elementsMaxOccursUnbounded = new ArrayList<XSElementDeclaration>();
			getMaxOccursUnboundedElements(elementsMaxOccursUnbounded,
					xsParticle);

			boolean isEnd = xscmVal.endContentModel(state);
			int[] occurenceInfo = xscmVal.occurenceInfo(state);

			CMState startState = new CMState(possibleElements, isEnd, state,
					elementsMaxOccursUnbounded, occurenceInfo);
			LOGGER.debug("Start = {}", startState);

			Map<CMState, SchemaInformedGrammar> knownStates = new HashMap<CMState, SchemaInformedGrammar>();
			addNewState(knownStates, startState, isMixedContent);
			handleStateEntries(possibleElements, xscmVal, state, startState,
					knownStates, isMixedContent, elementsMaxOccursUnbounded);

			return knownStates.get(startState);
		}
	}

	abstract protected StartElementNS createStartElementNS(String uri);

	abstract protected StartElement translatElementDeclarationToFSA(
			XSElementDeclaration xsElementDeclaration) throws EXIException;

	private void handleStateEntries(List<XSObject> possibleElements,
			XSCMValidator xscmVal, int[] originalState, CMState startState,
			Map<CMState, SchemaInformedGrammar> knownStates,
			boolean isMixedContent,
			List<XSElementDeclaration> elementsMaxOccursUnbounded)
			throws EXIException {
		assert (knownStates.containsKey(startState));

		for (int ind = 0; ind < possibleElements.size(); ind++) {
			XSObject xs = possibleElements.get(ind);
			// copy state since it gets modified
			int[] cstate = new int[originalState.length];
			System.arraycopy(originalState, 0, cstate, 0, originalState.length);

			if (xs.getType() == XSConstants.ELEMENT_DECLARATION) {
				// make transition
				XSElementDeclaration nextEl = (XSElementDeclaration) xs;
				QName qname = new QName(null, nextEl.getName(), null,
						nextEl.getNamespace());

				Object nextRet = xscmVal.oneTransition(qname, cstate,
						subGroupHandler);

				// check whether right transition was taken
				assert (xs == nextRet);

				// next possible state
				@SuppressWarnings("unchecked")
				List<XSObject> nextPossibleElements = xscmVal
						.whatCanGoHere(cstate);
				boolean isEnd = xscmVal.endContentModel(cstate);
				int[] occurenceInfo = xscmVal.occurenceInfo(cstate);
				// int[] occs2 = xscmVal.occurenceInfo(originalState);

				CMState nextState = new CMState(nextPossibleElements, isEnd,
						cstate, elementsMaxOccursUnbounded, occurenceInfo);

				printTransition(startState, xs, nextState);

				// retrieve list of possible elements (e.g. substitution group
				// elements)
				List<XSElementDeclaration> elements = getPossibleElementDeclarations(nextEl);
				assert (elements.size() > 0);
				boolean isNewState = false;

				for (int i = 0; i < elements.size(); i++) {
					XSElementDeclaration nextEN = elements.get(i);
					// Event xsEvent = getStartElement(nextEN);
					Event xsEvent = translatElementDeclarationToFSA(nextEN);
					if (i == 0) {
						// first element tells the right way to proceed
						isNewState = handleStateEntry(startState, knownStates,
								xsEvent, nextState, isMixedContent);
					} else {
						handleStateEntry(startState, knownStates, xsEvent,
								nextState, isMixedContent);
					}
				}

				if (isNewState) {
					handleStateEntries(nextPossibleElements, xscmVal, cstate,
							nextState, knownStates, isMixedContent,
							elementsMaxOccursUnbounded);
				}

			} else {
				assert (xs.getType() == XSConstants.WILDCARD);
				XSWildcard nextWC = ((XSWildcard) xs);
				short constraintType = nextWC.getConstraintType();
				if (constraintType == XSWildcard.NSCONSTRAINT_ANY
						|| constraintType == XSWildcard.NSCONSTRAINT_NOT) {
					// make transition
					QName qname = new QName(null, "##wc", null, "");
					Object nextRet = xscmVal.oneTransition(qname, cstate,
							subGroupHandler);
					// check whether right transition was taken
					assert (xs == nextRet);

					// next possible state
					@SuppressWarnings("unchecked")
					List<XSObject> nextPossibleElements = xscmVal
							.whatCanGoHere(cstate);
					boolean isEnd = xscmVal.endContentModel(cstate);
					int[] occurenceInfo = xscmVal.occurenceInfo(cstate);
					CMState nextState = new CMState(nextPossibleElements,
							isEnd, cstate, elementsMaxOccursUnbounded,
							occurenceInfo);

					printTransition(startState, xs, nextState);

					Event xsEvent = START_ELEMENT_GENERIC;

					boolean isNewState = handleStateEntry(startState,
							knownStates, xsEvent, nextState, isMixedContent);
					if (isNewState) {
						handleStateEntries(nextPossibleElements, xscmVal,
								cstate, nextState, knownStates, isMixedContent,
								elementsMaxOccursUnbounded);
					}

				} else {
					assert (constraintType == XSWildcard.NSCONSTRAINT_LIST);
					// make transition
					StringList sl = nextWC.getNsConstraintList();
					QName qname = new QName(null, "##wc", null, sl.item(0));
					Object nextRet = xscmVal.oneTransition(qname, cstate,
							subGroupHandler);
					assert (xs == nextRet); // check whether right transition
					// was taken

					// next possible state
					@SuppressWarnings("unchecked")
					List<XSObject> nextPossibleElements = xscmVal
							.whatCanGoHere(cstate);
					boolean isEnd = xscmVal.endContentModel(cstate);
					int[] occurenceInfo = xscmVal.occurenceInfo(cstate);
					CMState nextState = new CMState(nextPossibleElements,
							isEnd, cstate, elementsMaxOccursUnbounded,
							occurenceInfo);

					printTransition(startState, xs, nextState);

					for (int i = 0; i < sl.getLength(); i++) {
						String namespaceURI = sl.item(i);
						addNamespaceStringEntry(namespaceURI);
						// Event xsEvent = new StartElementNS(namespaceURI);
						Event xsEvent = createStartElementNS(namespaceURI);
						boolean isNewState = handleStateEntry(startState,
								knownStates, xsEvent, nextState, isMixedContent);
						if (isNewState) {
							handleStateEntries(nextPossibleElements, xscmVal,
									cstate, nextState, knownStates,
									isMixedContent, elementsMaxOccursUnbounded);
						}
					}
				}
			}
		}
	}

	abstract protected void addLocalNameStringEntry(String namespaceURI,
			String localName);

	abstract protected List<String> addNamespaceStringEntry(String namespaceURI);

	/**
	 * 
	 * Creates/Modifies appropriate rule and return whether the next state has
	 * been already resolved. If returnValue == TRUE it is a new state which
	 * requires further processing. If the returnValue == FALSE the according
	 * state(rule) is already full evaluated
	 * 
	 * @param startState
	 * @param knownStates
	 * @param xsEvent
	 * @param nextState
	 * @return requires further processing of nextState
	 */
	private boolean handleStateEntry(CMState startState,
			Map<CMState, SchemaInformedGrammar> knownStates, Event xsEvent,
			CMState nextState, boolean isMixedContent) {
		SchemaInformedGrammar startRule = knownStates.get(startState);

		// System.out.println(knownStates);
		if (knownStates.containsKey(nextState)) {
			startRule.addProduction(xsEvent, knownStates.get(nextState));
			return false;
		} else {
			addNewState(knownStates, nextState, isMixedContent);
			startRule.addProduction(xsEvent, knownStates.get(nextState));
			return true;
		}
	}

	/**
	 * Returns a list of possible elements. In general this list is the element
	 * itself. In case of SubstitutionGroups the list is extended by all
	 * possible "replacements". The returned list is sorted lexicographically
	 * first by {name} then by {target namespace}.
	 * 
	 * (see http://www.w3.org/TR/exi/#elementTerms)
	 * 
	 * @param el
	 *            element
	 * @return list of possible elements
	 */
	protected List<XSElementDeclaration> getPossibleElementDeclarations(
			XSElementDeclaration el) {

		List<XSElementDeclaration> listElements = new ArrayList<XSElementDeclaration>();

		// add element itself
		listElements.add(el);

		// add possible substitution group elements
		XSNamedMap globalElements = xsModel
				.getComponents(XSConstants.ELEMENT_DECLARATION);
		// Note: no global elements in XSD cause error
		if (globalElements != null && globalElements.size() > 0) {
			XSObjectList listSG = xsModel.getSubstitutionGroup(el);
			if (listSG != null && listSG.getLength() > 0) {
				for (int i = 0; i < listSG.getLength(); i++) {
					XSElementDeclaration ed = (XSElementDeclaration) listSG
							.item(i);
					listElements.add(ed);
				}
			}
		}

		// sort list
		Collections.sort(listElements, elementDeclSort);

		return listElements;
	}

	private static void printTransition(CMState startState, XSObject xs,
			CMState nextState) {
		LOGGER.debug("\t{} --> {} --> {}", startState, xs, nextState);
	}

	/*
	 * XMLErrorHandler
	 */
	public void error(String domain, String key, XMLParseException exception)
			throws XNIException {
		schemaParsingErrors.add("[xs-error] " + exception.getMessage());
	}

	public void fatalError(String domain, String key,
			XMLParseException exception) throws XNIException {
		schemaParsingErrors.add("[xs-fatalError] " + exception.getMessage());
	}

	public void warning(String domain, String key, XMLParseException exception)
			throws XNIException {
		schemaParsingErrors.add("[xs-warning] " + exception.getMessage());
	}

	static class XSElementDeclarationSort implements
			Comparator<XSElementDeclaration> {
		public int compare(XSElementDeclaration e1, XSElementDeclaration e2) {
			return QNameSort.compare(e1.getNamespace(), e1.getName(),
					e2.getNamespace(), e2.getName());
		}
	}

	static class XSAttributeDeclarationSort implements
			Comparator<XSAttributeDeclaration> {
		public int compare(XSAttributeDeclaration a1, XSAttributeDeclaration a2) {
			return QNameSort.compare(a1.getNamespace(), a1.getName(),
					a2.getNamespace(), a2.getName());
		}
	}

	static class XSAttributeUseSort implements Comparator<XSAttributeUse> {
		public int compare(XSAttributeUse a1, XSAttributeUse a2) {
			return attributeDeclSort.compare(a1.getAttrDeclaration(),
					a2.getAttrDeclaration());
		}
	}

	/*
	 * Internal Helper Class: CMState
	 */
	static class CMState {
		protected final List<XSObject> states;
		protected final boolean end;
		protected final int[] state;
		protected final List<XSElementDeclaration> elementsMaxOccursUnbounded;
		protected final int[] occurenceInfo;

		public CMState(List<XSObject> states, boolean end, int[] state,
				List<XSElementDeclaration> elementsMaxOccursUnbounded,
				int[] occurenceInfo) {
			this.states = states;
			this.end = end;
			this.elementsMaxOccursUnbounded = elementsMaxOccursUnbounded;
			this.occurenceInfo = occurenceInfo;
			// copy, may get modified
			this.state = new int[state.length];
			System.arraycopy(state, 0, this.state, 0, state.length);
		}

		public boolean equals(Object o) {
			if (o instanceof CMState) {
				CMState other = (CMState) o;
				if (end == other.end && states.equals(other.states)) {
					// return(Arrays.equals(state, other.state)) ;
					assert (state.length > 1 && other.state.length > 1);

					// NOTE: 3rd item is counter only!
					if (state[0] == other.state[0]
							&& state[1] == other.state[1]) {
						if (states.size() == 0 && other.states.size() == 0) {
							return true;
						} else if (state[2] != other.state[2]) {
							// any element maxOccurs unbounded
							for (int i = 0; i < states.size(); i++) {
								XSObject s = states.get(i);
								if (elementsMaxOccursUnbounded.contains(s)) {
									// If an array is returned it will have a
									// length == 4 and will contain:
									//
									// a[0] :: min occurs
									// a[1] :: max occurs
									// a[2] :: current value of the counter
									// a[3] :: identifier for the repeating term
									if (this.occurenceInfo == null) {
										return true;
									} else {
										// int currThis = this.occurenceInfo ==
										// null ? -1
										// : this.occurenceInfo[2];
										// int currOther = other.occurenceInfo
										// == null ? -1
										// : other.occurenceInfo[2];

										assert (this.occurenceInfo[0] == other.occurenceInfo[0]);
										return (this.occurenceInfo[2] >= this.occurenceInfo[0] && other.occurenceInfo[2] >= this.occurenceInfo[0]);

									}
									// return true;
								}
							}
							return false;
						} else {
							return true;
						}
					}

				}
			}
			return false;
		}

		public String toString() {
			return (end ? "F" : "N") + stateToString() + states.toString();
		}

		protected String stateToString() {
			StringBuilder s = new StringBuilder();
			s.append('(');
			for (int i = 0; i < state.length; i++) {
				s.append(state[i]);
				if (i < (state.length - 1)) {
					s.append(',');
				}
			}
			s.append(')');

			return s.toString();
		}

		public int hashCode() {
			return end ? states.hashCode() : -states.hashCode();
		}
	}

}
