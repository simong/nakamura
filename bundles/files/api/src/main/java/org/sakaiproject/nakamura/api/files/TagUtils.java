/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sakaiproject.nakamura.api.files;

import static org.apache.sling.jcr.resource.JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY;
import static org.sakaiproject.nakamura.api.files.FilesConstants.RT_SAKAI_TAG;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAGS;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_COUNT;
import static org.sakaiproject.nakamura.api.files.FilesConstants.SAKAI_TAG_NAME;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.PathUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class TagUtils {

  public static final String CONTENT_MANAGER_LABEL = ", contentManager:";

  private TagUtils() {

  }

  /**
   * Check if a node is a proper sakai tag.
   *
   * @param node
   *          The node to check if it is a tag.
   * @return true if the node is a tag, false if it is not.
   */
  public static boolean isTag(Content node) {
    return (node != null && node.hasProperty(SLING_RESOURCE_TYPE_PROPERTY)
        && node.hasProperty(SAKAI_TAG_NAME)
 && RT_SAKAI_TAG.equals(node
        .getProperty(SLING_RESOURCE_TYPE_PROPERTY)));
  }

  /**
   * @param cm a <code>ContentManager</code> for updating the content node with tags
   * @param contentNode the <code>Content</code> to be tagged
   * @param tagNodes <code>List</code> of <code>Content</code> representing the tags to add
   * @return A list of all tags that were applied. Check for dropped tags with
   *         tagNode.length != addTag(contentNode, tagNode).size()
   */
  public static List<Content> addTags(ContentManager cm, Content contentNode,
      List<Content> tagNodes) throws StorageClientException, AccessDeniedException {
    // input validation
    if (contentNode == null || cm == null) {
      throw new IllegalArgumentException("Missing a required argument:: contentNode:" + contentNode
          + CONTENT_MANAGER_LABEL + cm);
    }

    List<Content> addedTags = Lists.newArrayList();
    if (tagNodes != null) {
      String[] tagNames = PropertiesUtil.toStringArray(contentNode.getProperty(SAKAI_TAGS), new String[0]);
      Set<String> nameSet = Sets.newHashSet(tagNames);
      for (Content tagNode : tagNodes) {
        String tagName = String.valueOf(tagNode.getProperty(SAKAI_TAG_NAME));
        if (!nameSet.contains(tagName)) {
          nameSet.add(tagName);
          addedTags.add(tagNode);
        }
      }
      if (addedTags.size() > 0) {
        contentNode.setProperty(SAKAI_TAGS, nameSet.toArray(new String[nameSet.size()]));
        cm.update(contentNode);
      }
    }
    return addedTags;
  }

  public static boolean deleteTag(ContentManager contentManager, Content content,
      String tag) throws AccessDeniedException, StorageClientException {
    // input validation
    if (content == null || contentManager == null) {
      throw new IllegalArgumentException("Missing a required argument:: content:" + content
          + CONTENT_MANAGER_LABEL + contentManager);
    }

    if (StringUtils.isBlank(tag)) {
      return false;
    }

    boolean updated = false;
    String[] tagNames = PropertiesUtil.toStringArray(content.getProperty(SAKAI_TAGS), new String[0]);
    Set<String> nameSet = Sets.newHashSet(tagNames);
    if (nameSet.contains(tag)) {
      nameSet.remove(tag);
      content.setProperty(SAKAI_TAGS,
          nameSet.toArray(new String[nameSet.size()]));
      updated = true;
    }
    if (updated) {
      contentManager.update(content);
      return true;
    }
    return false;
  }

  public static Collection<String> ancestorTags(Content tagNode, ContentManager cm)
      throws AccessDeniedException, StorageClientException {
    // input validation
    if (tagNode == null || cm == null) {
      throw new IllegalArgumentException("Missing a required argument:: tagNode:" + tagNode
          + CONTENT_MANAGER_LABEL + cm);
    }

    Collection<String> rv = new ArrayList<String>();
    if (!isChildOfRoot(tagNode)) {
      String parentPath = PathUtils.getParentReference(tagNode.getPath());
      Content parentNode = cm.get(parentPath);
      if (parentNode != null) {
        if (TagUtils.isTag(parentNode)) {
          rv.add(String.valueOf(parentNode.getProperty(SAKAI_TAG_NAME)));
        }
        rv.addAll(ancestorTags(parentNode, cm));
      }
    }
    return rv;
  }

  public static boolean isChildOfRoot(Content node) {
    if (node == null) {
      throw new IllegalArgumentException("Missing a required argument:: node");
    }

    String parentPath = PathUtils.getParentReference(node.getPath());
    return "".equals(parentPath) || "/".equals(parentPath);
  }

  /**
   * Test if any of <code>tagNames</code> exist below <code>tagNode</code>.
   *
   * @param tagNode Starting point of hierarchy search.
   * @param tagNames Names to look for.
   * @param cm
   * @return
   * @throws StorageClientException
   */
  public static boolean alreadyTaggedBelowThisLevel(Content tagNode, String[] tagNames,
      ContentManager cm) throws StorageClientException {
    // input validation
    if (cm == null || tagNode == null) {
      throw new IllegalArgumentException("Missing a required argument:: tagNode:" + tagNode
          + CONTENT_MANAGER_LABEL + cm);
    }

    if (tagNames != null && tagNames.length > 0) {
      List<String> tagNamesList = Arrays.asList(tagNames);
      Iterator<Content> childNodes = cm.listChildren(tagNode.getPath());
      while(childNodes.hasNext()){
        Content child = childNodes.next();
        // check the current node first so we don't recurse unnecessarily
        if (TagUtils.isTag(child)
            && tagNamesList.contains(String.valueOf(child.getProperty(SAKAI_TAG_NAME)))) {
          return true;
        }
        // recurse to see if we're tagged somewhere down the tree
        if (alreadyTaggedBelowThisLevel(child, tagNames, cm)) {
          return true;
        }
      }
    }
    return false;
  }
  
  public static boolean alreadyTaggedAtOrAboveThisLevel(String[] tagNames, List<String> peerTags) {
    if (tagNames != null && peerTags != null && peerTags.size() > 0) {
      for (String tagName : tagNames) {
        if (peerTags.contains(tagName)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void bumpTagCounts(Content nodeTag, String[] tagNames, boolean increase,
      boolean calledByAChild, ContentManager cm) throws StorageClientException,
      AccessDeniedException {
    // input validation
    if (nodeTag == null || cm == null) {
      throw new IllegalArgumentException("Missing a required argument:: nodeTag:" + nodeTag
          + CONTENT_MANAGER_LABEL + cm);
    }

    if (calledByAChild || !TagUtils.alreadyTaggedBelowThisLevel(nodeTag, tagNames, cm)) {
      Long tagCount;
      if (nodeTag.hasProperty(SAKAI_TAG_COUNT)) {
        tagCount = StorageClientUtils.toLong(nodeTag.getProperty(SAKAI_TAG_COUNT));
      } else {
        tagCount = 0L;
      }
      if (increase) {
        tagCount++;
      } else {
        tagCount--;
      }
      nodeTag.setProperty(SAKAI_TAG_COUNT, tagCount);
      cm.update(nodeTag);
    }

     // if this node's parent is not the root, we keep going up
    if (!TagUtils.isChildOfRoot(nodeTag)) {
      List<String> peerTags = new ArrayList<String>();
      peerTags.addAll(TagUtils.ancestorTags(nodeTag, cm));
      String parentPath = PathUtils.getParentReference(nodeTag.getPath());
      Iterator<Content> peers = cm.listChildren(parentPath);
      while (peers.hasNext()) {
        Content peer = peers.next();
        if (TagUtils.isTag(peer) && !nodeTag.getPath().equals(peer.getPath())) {
          peerTags.add(String.valueOf(peer.getProperty(SAKAI_TAG_NAME)));
        }
      }
      if (!TagUtils.alreadyTaggedAtOrAboveThisLevel(tagNames, peerTags)) {
        Content parentNode = cm.get(parentPath);
        bumpTagCounts(parentNode, tagNames, increase, true, cm);
      }
    }
  }
}
