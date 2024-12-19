package com.v01.techgear_server.mapping.mapper;

import java.util.Collections;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.v01.techgear_server.dto.WishlistDTO;
import com.v01.techgear_server.dto.WishlistItemsDTO;
import com.v01.techgear_server.model.Wishlist;
import com.v01.techgear_server.model.WishlistItems;
import com.v01.techgear_server.utils.BaseMapper;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, uses = {
        ImageMapper.class,
        WishlistItemsMapper.class,
        AccountDetailsMapper.class
}, nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT, nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface WishlistMapper extends BaseMapper<Wishlist, WishlistDTO> {
    // Override and customize base methods with specific mappings
    @Override
    @Mapping(target = "wishlistId", source = "wishlistId")
    @Mapping(target = "wishlistDescription", source = "wishlistDescription")
    @Mapping(target = "createdDate", source = "createdDate")
    @Mapping(target = "lastUpdatedDate", source = "lastUpdatedDate")
    @Mapping(target = "totalValue", source = "totalValue")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "wishlistImage", source = "wishlistImage")
    @Mapping(target = "notifySale", source = "notifySale")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapWishlistItems")
    @Mapping(target = "accountDetails", source = "accountDetails")
    WishlistDTO toDTO(Wishlist wishlist);

    @Override
    @Mapping(target = "wishlistId", source = "wishlistId")
    @Mapping(target = "wishlistDescription", source = "wishlistDescription")
    @Mapping(target = "createdDate", source = "createdDate")
    @Mapping(target = "lastUpdatedDate", source = "lastUpdatedDate")
    @Mapping(target = "totalValue", source = "totalValue")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "wishlistImage", source = "wishlistImage")
    @Mapping(target = "notifySale", source = "notifySale")
    @Mapping(target = "items", source = "items", qualifiedByName = "mapWishlistItems")
    @Mapping(target = "accountDetails", source = "accountDetails")
    Wishlist toEntity(WishlistDTO wishlistDTO);

    // Default implementation for bulk mapping methods
    @Override
    default List<WishlistDTO> toDTOList(List<Wishlist> entityList) {
        if (entityList == null) {
            return Collections.emptyList();
        }
        return entityList.stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    default List<Wishlist> toEntityList(List<WishlistDTO> dtoList) {
        if (dtoList == null) {
            return Collections.emptyList();
        }
        return dtoList.stream()
                .map(this::toEntity)
                .toList();
    }

    // Named method for complex item mapping
    @Named("mapWishlistItems")
    default List<WishlistItemsDTO> mapWishlistItems(List<WishlistItems> items) {
        if (items == null) {
            return Collections.emptyList();
        }
        return items.stream()
                .map(this::mapWishlistItem)
                .toList();
    }

    // Custom item mapping method
    WishlistItemsDTO mapWishlistItem(WishlistItems item);

}
