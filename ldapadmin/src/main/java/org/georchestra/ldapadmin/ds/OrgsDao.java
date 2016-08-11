/*
 * Copyright (C) 2009-2016 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.ldapadmin.ds;


import org.georchestra.ldapadmin.dto.Org;
import org.georchestra.ldapadmin.dto.OrgExt;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.filter.Filter;
import org.springframework.ldap.support.LdapNameBuilder;

import javax.naming.Name;
import javax.naming.directory.*;
import javax.naming.NamingException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

/**
 * This class manage organization membership
 */
public class OrgsDao {

    private LdapTemplate ldapTemplate;
    private Name orgsSearchBaseDN;
    private Name userSearchBaseDN;
    private String basePath;


    public void setLdapTemplate(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public void setOrgsSearchBaseDN(String orgsSearchBaseDN) {
        this.orgsSearchBaseDN = LdapNameBuilder.newInstance(orgsSearchBaseDN).build();
    }

    public void setUserSearchBaseDN(String userSearchBaseDN) {
        this.userSearchBaseDN = LdapNameBuilder.newInstance(userSearchBaseDN).build();
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * Search all organization defined in ldap. this.orgsSearchBaseDN hold search path in ldap.
     *
     * @return list of organization
     */
    public List<Org> findAll(){
        EqualsFilter filter = new EqualsFilter("objectClass", "groupOfMembers");
        return ldapTemplate.search(this.orgsSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper());
    }

    /**
     * Search organization with 'commonName' as distinguish name
     * @param commonName distinguish name of organization for example : 'psc' to retreive
     *                   'cn=psc,ou=orgs,dc=georchestra,dc=org'
     * @return Org instance with specified DN
     */
    public Org findByCommonName(String commonName) {
        Name dn = LdapNameBuilder.newInstance(this.orgsSearchBaseDN).add("cn", commonName).build();
        return this.ldapTemplate.lookup(dn, new OrgsDao.OrgAttributesMapper());
    }

    /**
     * Search for organization extension with specified identifier
     * @param cn distinguish name of organization for example : 'psc' to retreive
     *           'o=psc,ou=orgs,dc=georchestra,dc=org'
     * @return OrgExt instance corresponding to extended attributes
     */
    public OrgExt findExtById(String cn) {
        Name dn = LdapNameBuilder.newInstance(this.orgsSearchBaseDN).add("o", cn).build();
        return this.ldapTemplate.lookup(dn, new OrgsDao.OrgExtAttributesMapper());
    }

    /**
     * Given user identifier, retrieve organization of this user.
     * @param user identifier of user (not a full DN), example : 'testadmin'
     * @return Org instance corresponding to organization of specified user or null if no organization is linked to
     * this user
     * @throws DataServiceException if more than one organization is linked to specified user
     */
    public Org findForUser(String user) throws DataServiceException {

        Name userDn = LdapNameBuilder.newInstance(this.userSearchBaseDN).add("uid", user).build();

        AndFilter filter  = new AndFilter();
        filter.and(new EqualsFilter("member", userDn.toString()));
        filter.and(new EqualsFilter("objectClass", "groupOfMembers"));
        List<Org> res = ldapTemplate.search(this.orgsSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper());
        if(res.size() > 1)
            throw new DataServiceException("Multiple org for user : " + user);
        if(res.size() == 1)
            return res.get(0);
        else
            return null;

    }

    public void insert(Org org){
        this.ldapTemplate.bind(buildOrgDN(org.getId()), null, buildAttributes(org));
    }

    public void insert(OrgExt org){
        this.ldapTemplate.bind(buildOrgExtDN(org.getId()), null, buildAttributes(org));
    }

    public void addUser(String organization, String user){
        DirContextOperations context = ldapTemplate.lookupContext(buildOrgDN(organization));
        context.addAttributeValue("member", buildUserDN(user).toString(), false);
        this.ldapTemplate.modifyAttributes(context);
    }

    public void removeUser(String organization, String user){
        DirContextOperations ctx = ldapTemplate.lookupContext(buildOrgDN(organization));
        ctx.removeAttributeValue("member", buildUserDN(user).toString());
        this.ldapTemplate.modifyAttributes(ctx);
    }

    private Name buildUserDN(String id){
        return LdapNameBuilder.newInstance(this.userSearchBaseDN + "," + this.basePath).add("uid", id).build();
    }

    private Name buildOrgDN(String id){
        return LdapNameBuilder.newInstance(this.orgsSearchBaseDN).add("cn", id).build();
    }

    private Name buildOrgExtDN(String id){
        return LdapNameBuilder.newInstance(this.orgsSearchBaseDN).add("o", id).build();
    }


    private Attributes buildAttributes(Org org) {
        Attributes attrs = new BasicAttributes();
        BasicAttribute ocattr = new BasicAttribute("objectclass");
        ocattr.add("top");
        ocattr.add("groupOfMembers");

        attrs.put(ocattr);
        attrs.put("cn", org.getId());
        attrs.put("o", org.getName());
        attrs.put("ou", org.getShortName());
        attrs.put("description", org.getCities());
        attrs.put("businessCategory", org.getStatus());
        attrs.put("seeAlso", this.buildOrgExtDN(org.getId()));

        return attrs;
    }

    private Attributes buildAttributes(OrgExt org) {
        Attributes attrs = new BasicAttributes();
        BasicAttribute ocattr = new BasicAttribute("objectclass");
        ocattr.add("top");
        ocattr.add("organization");

        attrs.put(ocattr);
        attrs.put("o", org.getId());
        attrs.put("businessCategory", org.getOrgType());
        attrs.put("postalAddress", org.getAddress());

        return attrs;
    }

    private class OrgAttributesMapper implements AttributesMapper<Org> {

        public Org mapFromAttributes(Attributes attrs) throws NamingException {
            Org org = new Org();
            org.setId(asString(attrs.get("cn")));
            org.setName(asString(attrs.get("o")));
            org.setShortName(asString(attrs.get("ou")));
            if(attrs.get("description") != null)
                org.setCities(Arrays.asList(asString(attrs.get("description")).split(",")));
            else
                org.setCities(new LinkedList<String>());
            org.setStatus(asString(attrs.get("businessCategory")));
            org.setMembers(asListString(attrs.get("member")));
            return org;
        }

        public String asString(Attribute att) throws NamingException {
            if(att == null)
                return null;
            else
                return (String) att.get();
        }

        public List<String> asListString(Attribute att) throws NamingException {
            List<String> res = new LinkedList<String>();

            if(att == null)
                return res;


            for(int i=0; i< att.size();i++)
                res.add((String) att.get(i));

            return res;
        }
    }

    private class OrgExtAttributesMapper implements AttributesMapper<OrgExt> {

        public OrgExt mapFromAttributes(Attributes attrs) throws NamingException {
            OrgExt org = new OrgExt();
            org.setId(asString(attrs.get("o")));
            org.setOrgType(asString(attrs.get("businessCategory")));
            org.setAddress(asString(attrs.get("postalAddress")));
            return org;
        }

        public String asString(Attribute att) throws NamingException {
            if(att == null)
                return null;
            else
                return (String) att.get();
        }
    }
}
