import { isEmpty } from "underscore";
import type { MouseEvent } from "react";
import { useLayoutEffect, useRef, useState } from "react";
import { t } from "ttag";
import type {
  SearchFilterComponentProps,
  SearchSidebarFilterComponent,
} from "metabase/search/types";
import { Button, Group, Text, Box, FocusTrap } from "metabase/ui";
import type { IconName } from "metabase/core/components/Icon";
import { Icon } from "metabase/core/components/Icon";
import Popover from "metabase/components/Popover";
import { useSelector } from "metabase/lib/redux";
import { getIsNavbarOpen } from "metabase/redux/app";
import useIsSmallScreen from "metabase/hooks/use-is-small-screen";
import { isNotNull } from "metabase/core/utils/types";
import EventSandbox from "metabase/components/EventSandbox";
import {
  DropdownApplyButtonDivider,
  DropdownClearButton,
  DropdownFieldSet,
  SearchPopoverContainer,
} from "./SidebarFilter.styled";

export type SearchSidebarFilterProps = {
  filter: SearchSidebarFilterComponent;
} & SearchFilterComponentProps;

export const SidebarFilter = ({
  filter: { title, iconName, DisplayComponent, ContentComponent },
  "data-testid": dataTestId,
  value,
  onChange,
}: SearchSidebarFilterProps) => {
  const [selectedValues, setSelectedValues] = useState(value);
  const [isPopoverOpen, setIsPopoverOpen] = useState(false);

  const isNavbarOpen = useSelector(getIsNavbarOpen);
  const isSmallScreen = useIsSmallScreen();

  const dropdownRef = useRef<HTMLDivElement>(null);
  const [popoverWidth, setPopoverWidth] = useState<string>();

  const fieldHasValue = Array.isArray(value)
    ? !isEmpty(value)
    : isNotNull(value);

  const handleResize = () => {
    if (dropdownRef.current) {
      const { width } = dropdownRef.current.getBoundingClientRect();
      setPopoverWidth(`${width}px`);
    }
  };

  useLayoutEffect(() => {
    if (!popoverWidth) {
      handleResize();
    }
    window.addEventListener("resize", handleResize, false);
    return () => window.removeEventListener("resize", handleResize, false);
  }, [dropdownRef, popoverWidth]);

  useLayoutEffect(() => {
    if (isNavbarOpen && isSmallScreen) {
      setIsPopoverOpen(false);
    }
  }, [isNavbarOpen, isSmallScreen]);

  const onApplyFilter = () => {
    onChange(selectedValues);
    setIsPopoverOpen(false);
  };

  const onClearFilter = (e: MouseEvent) => {
    if (fieldHasValue) {
      e.stopPropagation();
      setSelectedValues(undefined);
      onChange(undefined);
      setIsPopoverOpen(false);
    }
  };

  const onPopoverClose = () => {
    // reset selection to the current filter state
    setSelectedValues(value);
    setIsPopoverOpen(false);
  };

  const getDropdownIcon = (): IconName => {
    if (fieldHasValue) {
      return "close";
    } else {
      return isPopoverOpen ? "chevronup" : "chevrondown";
    }
  };

  return (
    <Box
      data-testid={dataTestId}
      ref={dropdownRef}
      onClick={() => setIsPopoverOpen(!isPopoverOpen)}
      w="100%"
    >
      <DropdownFieldSet
        noPadding
        legend={fieldHasValue ? title : null}
        fieldHasValueOrFocus={fieldHasValue}
      >
        <Group position="apart" noWrap w="100%">
          {fieldHasValue ? (
            <DisplayComponent value={value} />
          ) : (
            <Group noWrap>
              <Icon name={iconName} />
              <Text weight={700}>{title}</Text>
            </Group>
          )}
          <DropdownClearButton
            data-testid="sidebar-filter-dropdown-button"
            compact
            c="inherit"
            variant="subtle"
            onClick={onClearFilter}
            leftIcon={<Icon name={getDropdownIcon()} />}
          />
        </Group>
      </DropdownFieldSet>

      <Popover
        isOpen={isPopoverOpen}
        onClose={onPopoverClose}
        target={dropdownRef.current}
        ignoreTrigger
        autoWidth
        sizeToFit
      >
        <EventSandbox>
          <FocusTrap active>
            <SearchPopoverContainer w={popoverWidth ?? "100%"} spacing={0}>
              <ContentComponent
                value={selectedValues}
                onChange={selected => setSelectedValues(selected)}
              />
              <DropdownApplyButtonDivider />
              <Group position="right" align="center" px="sm" pb="sm">
                <Button onClick={onApplyFilter}>{t`Apply filters`}</Button>
              </Group>
            </SearchPopoverContainer>
          </FocusTrap>
        </EventSandbox>
      </Popover>
    </Box>
  );
};
