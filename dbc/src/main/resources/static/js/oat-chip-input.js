class OatChipInput extends HTMLElement {
  constructor() {
    super();
    this.tags = [];
    this._name = '';
  }

  static get observedAttributes() {
    return ['placeholder', 'name', 'value'];
  }

  connectedCallback() {
    this.render();
    this.setup();
  }

  attributeChangedCallback(name, oldValue, newValue) {
    if (name === 'placeholder' && this.input) {
      this.input.placeholder = newValue || 'Add item...';
    }
    if (name === 'name') {
      this._name = newValue || '';
      this.updateHiddenInput();
    }
    if (name === 'value' && newValue !== oldValue) {
      this.setValue(newValue);
    }
  }

  render() {
    this.innerHTML = `
      <input 
        type="text" 
        class="chip-input" 
        placeholder="${this.getAttribute('placeholder') || 'Add item...'}"
      >
      <input type="hidden" class="chip-hidden-input">
    `;
  }

  setup() {
    this.input = this.querySelector('.chip-input');
    this.hiddenInput = this.querySelector('.chip-hidden-input');
    this._name = this.getAttribute('name') || '';

    this.classList.add('chip-input-wrapper');

    this.setupListeners();
    this.renderChips();
    this.updateHiddenInput();
  }

  setupListeners() {
    const addTag = (text) => {
      text = text.trim();
      if (!text) return;

      if (this.tags.includes(text)) {
        this.input.value = '';
        return;
      }

      this.tags.push(text);
      this.renderChips();
      this.input.value = '';
      this.updateHiddenInput();
      this.dispatchChange();
    };

    this.input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        addTag(this.input.value);
      }

      // Backspace 删除最后一个 tag（当输入框为空时）
      if (e.key === 'Backspace' && this.input.value === '' && this.tags.length > 0) {
        this.tags.pop();
        this.renderChips();
        this.updateHiddenInput();
        this.dispatchChange();
      }
    });

    // 点击组件任意位置聚焦输入框
    this.addEventListener('click', () => this.input.focus());
  }

  renderChips() {
    this.querySelectorAll('.chip').forEach(el => el.remove());

    this.tags.forEach((tag, index) => {
      const chip = document.createElement('button');
      chip.className = 'chip';
      chip.type = 'button';
      chip.innerHTML = `
        <span>${tag}</span>
        <span class="chip-close" data-index="${index}">×</span>
      `;

      chip.querySelector('.chip-close').addEventListener('click', (e) => {
        e.stopPropagation();
        this.tags.splice(index, 1);
        this.renderChips();
        this.updateHiddenInput();
        this.dispatchChange();
      });

      this.insertBefore(chip, this.input);
    });
  }

  updateHiddenInput() {
    if (this.hiddenInput && this._name) {
      this.hiddenInput.name = this._name;
      this.hiddenInput.value = JSON.stringify(this.tags);
    }
  }

  dispatchChange() {
    this.dispatchEvent(new CustomEvent('change', {
      detail: { value: [...this.tags] },
      bubbles: true
    }));
  }

  get value() {
    return [...this.tags];
  }

  set value(newValue) {
    this.setValue(newValue);
  }

  setValue(newValue) {
    if (Array.isArray(newValue)) this.tags = [...newValue];
    else if (typeof newValue === 'string') {
      this.tags = newValue.split(',').map(t => t.trim()).filter(Boolean);
    } else {
      this.tags = [];
    }
    this.renderChips();
    this.updateHiddenInput();
    this.dispatchChange();
  }

  clear() {
    this.tags = [];
    this.renderChips();
    this.updateHiddenInput();
    this.dispatchChange();
  }
}

customElements.define('oat-chip-input', OatChipInput);