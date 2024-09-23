package com.condation.cms.modules.forms.actions;

import com.condation.cms.modules.forms.FormsConfig;
import org.eclipse.jetty.server.Request;

/**
 *
 * @author t.marx
 */
public interface FormsAction {
	
	void execute (FormsConfig.Form form, Request request);
}
