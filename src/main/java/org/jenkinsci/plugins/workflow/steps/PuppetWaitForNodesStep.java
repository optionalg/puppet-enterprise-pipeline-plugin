package org.jenkinsci.plugins.workflow.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.*;
import com.google.inject.Inject;
import hudson.Extension;
import hudson.Util;
import hudson.util.ListBoxModel;
import hudson.security.ACL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.apache.commons.lang.StringUtils;
import hudson.model.Run;
import hudson.model.Item;
import hudson.model.TaskListener;
import java.net.*;
import jenkins.model.Jenkins;
import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.plaincredentials.*;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.AncestorInPath;
import java.io.Serializable;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.google.gson.internal.LinkedTreeMap;

import org.jenkinsci.plugins.puppetenterprise.PuppetEnterpriseManagement;
import org.jenkinsci.plugins.puppetenterprise.models.PuppetJob;
import org.jenkinsci.plugins.puppetenterprise.apimanagers.puppetorchestratorv1.PuppetOrchestratorException;
import org.jenkinsci.plugins.puppetenterprise.models.PEException;

public final class PuppetWaitForNodesStep extends PuppetEnterpriseStep implements Serializable {

  private ArrayList<String> nodes = new ArrayList();
  private String credentialsId = "";

  @DataBoundSetter private void setNodes(ArrayList nodes) {
    this.nodes = nodes;
  }

  public ArrayList getNodes() {
    return this.nodes;
  }

  @DataBoundConstructor public PuppetWaitForNodesStep() { }

  public static class PuppetWaitForNodesStepExecution extends AbstractSynchronousStepExecution<Void> {

    @Inject private transient PuppetWaitForNodesStep step;
    @StepContextParameter private transient Run<?, ?> run;
    @StepContextParameter private transient TaskListener listener;

    @Override protected Void run() throws Exception {
      PuppetJob job = new PuppetJob();
      job.setInventory(step.getNodes());
      job.setToken(step.getToken());
      job.setLogger(listener.getLogger());

      try {
        String summary = "";
        Integer iterations = 0;

        listener.getLogger().println("Waiting for up to 30 minutes for nodes to connect to Puppet Enterprise.");

        do {
          try {
            Thread.sleep(500);
            iterations = iterations + 1;
          } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
        } while (!job.allNodesConnected() && iterations < 3600);

        try {
          listener.getLogger().println("All " + step.getNodes().size() + " are now connected.");
        } catch(Exception e) {
          StringBuilder bug = new StringBuilder();
          bug.append("You found a bug! The Puppet Enterprise plugin received something ");
          bug.append("in a job report it wasn't expecting. Please file a ticket here: ");
          bug.append("https://issues.jenkins-ci.org/browse/JENKINS-42899?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20'puppet-enterprise-pipeline-plugin'\n\n");
          bug.append("Include the following information:\n");
          bug.append("Exception Type: " + e.getClass().getSimpleName() + "\n");
          bug.append("Exception Message: " + e.getMessage() + "\n");

          throw new Exception(bug.toString());
        }

      } catch(PuppetOrchestratorException e) {
        StringBuilder message = new StringBuilder();
        message.append("Puppet Orchestrator Job Error\n");
        message.append("Kind:    " + e.getKind() + "\n");
        message.append("Message: " + e.getMessage() + "\n");

        if (e.getDetails() != null) {
          message.append("Details: " + e.getDetails().toString() + "\n");
        }

        throw new PEException(message.toString(), listener);
      }

      return null;
    }

    private static final long serialVersionUID = 1L;
  }

  @Extension public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
    public DescriptorImpl() {
      super(PuppetWaitForNodesStepExecution.class);
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String source) {
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new ListBoxModel();
      }
      return new StandardListBoxModel().withEmptySelection().withAll(
      CredentialsProvider.lookupCredentials(StringCredentials.class, context, ACL.SYSTEM, URIRequirementBuilder.fromUri(source).build()));
    }

    @Override public String getFunctionName() {
      return "puppetWaitForNodes";
    }

    @Override public String getDisplayName() {
      return "Wait for nodes to join the Puppet Enterprise orchestrator";
    }
  }
}
