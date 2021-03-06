package com.github.emailtohl.pad.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Basic;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityResult;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.FieldResult;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.QueryHint;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.SqlResultSetMappings;
import javax.validation.constraints.NotNull;

import org.hibernate.envers.Audited;
import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.springframework.format.annotation.DateTimeFormat;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.github.emailtohl.pad.jpa.EntityBase;
import com.github.emailtohl.pad.jpa.Instruction;
import com.github.emailtohl.pad.jpa.Operator;
import com.github.emailtohl.pad.jpa.StringBridgeCustomization;

@NamedQueries({
    @NamedQuery(
        name = "findItemById",
        query = "select i from Item i where i.id = :id"
    )
    ,
    @NamedQuery(
        name = "findItemByName",
        query = "select i from Item i where i.name like :name",
        hints = {
            @QueryHint(
                name = org.hibernate.annotations.QueryHints.TIMEOUT_JPA,
                value = "60000"),
            @QueryHint(
                name = org.hibernate.annotations.QueryHints.COMMENT,
                value = "Custom SQL comment")
        }
    )
})
@SqlResultSetMappings({
    @SqlResultSetMapping(
        name = "ItemResult",
        entities =
        @EntityResult(
            entityClass = Item.class,
            fields = {
                @FieldResult(name = "id", column = "ID"),
                @FieldResult(name = "name", column = "EXTENDED_NAME"),
                @FieldResult(name = "createdOn", column = "CREATEDON"),
                @FieldResult(name = "auctionEnd", column = "AUCTIONEND"),
                @FieldResult(name = "auctionType", column = "AUCTIONTYPE"),
                @FieldResult(name = "approved", column = "APPROVED"),
                @FieldResult(name = "buyNowPrice", column = "BUYNOWPRICE"),
                @FieldResult(name = "seller", column = "SELLER_ID")
            }
        )
    )
})
@Audited
@Indexed
@Entity
public class Item extends EntityBase {
	private static final long serialVersionUID = -872160419878214733L;

	@NotNull
    protected String name;

    @NotNull
    protected Date createdOn = new Date();

    @NotNull
    protected Date auctionEnd;

    @NotNull
    protected AuctionType auctionType = AuctionType.HIGHEST_BID;

    @NotNull
    protected boolean approved = true;

    protected BigDecimal buyNowPrice;

    protected Participator seller;

    protected Set<Category> categories = new HashSet<>();

    protected Set<Bid> bids = new HashSet<>();

    protected Set<Image> images = new HashSet<>();

    public Item() {
    }

    public Item(String name, Date auctionEnd, Participator seller) {
        this.name = name;
        this.auctionEnd = auctionEnd;
        this.seller = seller;
    }

    @Field
    @Basic
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	public Date getAuctionEnd() {
        return auctionEnd;
    }

    public void setAuctionEnd(Date auctionEnd) {
        this.auctionEnd = auctionEnd;
    }

    @Field(bridge = @FieldBridge(impl = StringBridgeCustomization.class))
    @Enumerated(EnumType.STRING)
    public AuctionType getAuctionType() {
        return auctionType;
    }

    public void setAuctionType(AuctionType auctionType) {
        this.auctionType = auctionType;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    @Instruction(operator = Operator.GTE)
    public BigDecimal getBuyNowPrice() {
        return buyNowPrice;
    }

    public void setBuyNowPrice(BigDecimal buyNowPrice) {
        this.buyNowPrice = buyNowPrice;
    }

    @IndexedEmbedded(depth = 1)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(foreignKey = @ForeignKey(name = "FK_ITEM_SELLER_ID"))
    public Participator getSeller() {
        return seller;
    }

    public void setSeller(Participator seller) {
        this.seller = seller;
    }

    @IndexedEmbedded(depth = 1)
    @JsonBackReference("categories")
    @ManyToMany(mappedBy = "items")
    public Set<Category> getCategories() {
        return categories;
    }

    public void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    @IndexedEmbedded(depth = 1)
    @ContainedIn
    @JsonBackReference("bids")
    @OneToMany(mappedBy = "item")
    public Set<Bid> getBids() {
        return bids;
    }

    public void setBids(Set<Bid> bids) {
        this.bids = bids;
    }

    @IndexedEmbedded(depth = 1)
    @ElementCollection
    @JoinColumn(foreignKey = @ForeignKey(name = "FK_ITEM_IMAGES_ITEM_ID"))
    public Set<Image> getImages() {
        return images;
    }

    public void setImages(Set<Image> images) {
        this.images = images;
    }
    // ...
    @Override
    public Item clone() {
    	Item cp = (Item) super.clone();
    	if (seller != null) {
    		cp.seller = seller.clone();
    	}
    	cp.categories.addAll(categories.stream().map(Category::clone).collect(Collectors.toSet()));
    	// 避免循环调用，不复制bids的内容
    	cp.images.addAll(images.stream().map(Image::clone).collect(Collectors.toSet()));
    	return cp;
    }
}